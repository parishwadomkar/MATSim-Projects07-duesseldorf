package org.matsim.analysis;

import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A set of static methods to mark links in a network created from OSM, then extract these links to a new network.
 *
 * @author Pieter Fourie
 */
public class ExtractMinimalConnectedNetworkFromOSMUtilities {

	/**
	 * Use this to add a link attribute <tt>"keepLink" = true</tt> to network links
	 * lying inside a {@link Polygonal} geometry from the specified shapefile.
	 * <p>
	 * The input network is changed.
	 *
	 * @param polygonFileName
	 * @param network
	 */
	public static void networkSpatialJoinToBoundaryPolygon(String polygonFileName, Network network) {
		Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(new File(polygonFileName).getPath());

		Iterator<SimpleFeature> iterator = features.iterator();
		GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 25832);
		Polygonal polygon = null;
		while (iterator.hasNext()) {
			SimpleFeature feature = iterator.next();
			polygon = (Polygonal) feature.getDefaultGeometry();
		}
		//TODO: hope this cast works for single Polygons
		MultiPolygon finalPolygon = (MultiPolygon) polygon;
		network.getNodes().values().forEach(node -> {
			if (finalPolygon.contains(factory.createPoint(new Coordinate(node.getCoord().getX(), node.getCoord().getY())))) {
				node.getInLinks().values().forEach(link -> link.getAttributes().putAttribute("keepLink", true));
				node.getOutLinks().values().forEach(link -> link.getAttributes().putAttribute("keepLink", true));
			}
		});
		int numberOfIrrelevantLinks = network
				.getLinks()
				.values()
				.stream()
				.filter(link -> {
					Object keepLink = link.getAttributes().getAttribute("keepLink");
					return keepLink != null && (boolean) keepLink;
				})
				.collect(Collectors.toList())
				.size();
		System.out.println(String.format("This network has a total of %d links of which %d appear inside the city polygon boundary",
				network.getLinks().size(), numberOfIrrelevantLinks));
	}

	public static void main(String[] args) throws IOException {
		Network inputNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
		new MatsimNetworkReader(inputNetwork).readFile(args[0]);

		new org.matsim.core.network.algorithms.NetworkCleaner().run(inputNetwork);

		networkSpatialJoinToBoundaryPolygon(args[1], inputNetwork);

		markConnectedLinksOfQualifyingLevelInOSMHierarchy(inputNetwork, ConfigUtils.createConfig(), 1.5,
				0.2);

		new NetworkWriter(inputNetwork).write(args[3]);

		inputNetwork = extractNetworkContainingMarkedLinks(inputNetwork);

		new org.matsim.core.network.algorithms.NetworkCleaner().run(inputNetwork);

		// arb code to write out some values to display using SimWrapper
		new NetworkWriter(inputNetwork).write(args[4]);
		BufferedWriter bufferedWriter = IOUtils.getBufferedWriter(args[5]);
		bufferedWriter.write("link,cap\n");
		inputNetwork.getLinks().values().forEach(link -> {
			if (!link.getAllowedModes().contains(TransportMode.pt))
				try {
					bufferedWriter.write(link.getId() + "," + Math.min(link.getCapacity(), 6000d) + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
		});
		bufferedWriter.flush();
		bufferedWriter.close();
	}

	/**
	 * This creates a duplicate network where all links from the input network that have links with attribute
	 * <tt>"keeplink" = true</tt> are copied to, along wth their connecting nodes. Other methods in this class can be
	 * used to mark such links in a systematic way, or the links can be marked manually.
	 *
	 * @param inputNetwork
	 * @return
	 */
	public static Network extractNetworkContainingMarkedLinks(Network inputNetwork) {
		Network outputNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
		// use these sets to keep track if elements have already been added, so as to suppress warnings.
		Set<Node> addedNodes = new HashSet<>();
		Set<Link> addedLinks = new HashSet<>();
		inputNetwork.getLinks().values().forEach(link -> {
			if (link.getAttributes().getAttribute("keepLink") != null) {
				try {
					if (!addedNodes.contains(link.getFromNode()))
						outputNetwork.addNode(link.getFromNode());
				} catch (IllegalArgumentException e) {

				}
				try {
					if (!addedNodes.contains(link.getToNode()))
						outputNetwork.addNode(link.getToNode());

				} catch (IllegalArgumentException e) {

				}
				if (!addedLinks.contains(link))
					outputNetwork.addLink(link);

			}
		});
		return outputNetwork;
	}

	/**
	 * Based on the costs specified in {@link OSMHierarchyTravelDisutility}, takes the set of links with cost lower than
	 * <tt>maxOSMLinkTypeCost</tt>, shuffles them, reduces the set size to <tt>current size x sampleRate</tt>
	 * distributes the resulting set of links across available cores and processes each subset as follows.
	 * <p>
	 * The subset is divided into two, then a pair of qualifying links is produced by taking elements in each set in
	 * sequence. A path between the two links is calculated using {@link OSMHierarchyFavouringFastestPathCalculator},
	 * and all the links in the path, as well as links in the opposite direction, are given an attribute
	 * <tt>"keepLink" = true</tt>.
	 *
	 * @param network
	 * @param config
	 * @param maxOSMLinkTypeCost
	 * @param sampleRate
	 */
	public static void markConnectedLinksOfQualifyingLevelInOSMHierarchy(Network network, Config config,
																		 double maxOSMLinkTypeCost,
																		 double sampleRate) {
		Set<Id<Link>> linkstoKeep = new ConcurrentSkipListSet<>();
		AtomicInteger i = new AtomicInteger(0);

		network.getLinks()
				.values()
				.stream()
				.filter(
						link -> OSMHierarchyTravelDisutility.getOSMLinkTypeCost(link) <= maxOSMLinkTypeCost &&
								!link.getAllowedModes().contains(TransportMode.pt))
				.forEach(link -> linkstoKeep.add(link.getId()));


		class Runner implements Runnable {

			OSMHierarchyFavouringFastestPathCalculator pathCalculator = new OSMHierarchyFavouringFastestPathCalculator(network, config);
			final List<Id<Link>> myOriginLinks;
			final List<Id<Link>> myDestinationLinks;

			Runner(List<Id<Link>> myLinks) {
				this.myOriginLinks = myLinks.subList(0, myLinks.size() / 2);
				this.myDestinationLinks = myLinks.subList(myLinks.size() / 2, myLinks.size());
			}

			@Override
			public void run() {
//				for (Id<Link> link : myOriginLinks) {
//					for (Id<Link> dlink : myDestinationLinks) {
				for (int j = 0; j < Math.min(myOriginLinks.size(), myDestinationLinks.size()); j++) {
					Id<Link> link = myOriginLinks.get(j);
					Id<Link> dlink = myDestinationLinks.get(j);
					pathCalculator.getPath(network.getLinks().get(link).getToNode(),
							network.getLinks().get(dlink).getFromNode()).links.forEach(pathLink -> {
						linkstoKeep.add(pathLink.getId());
					});
//					pathCalculator.getPath(node, network.getLinks().get(link).getFromNode()).links.forEach(pathLink -> {
//						linkstoKeep.add(pathLink.getId());
//					});
					int localI = i.incrementAndGet();
					if (localI % 100 == 0)
						System.out.print(String.format("%06d:%06d...%s", i.get(), linkstoKeep.size(),
								(localI % 1000 == 0 ?
										"\n" :
										"")));

				}
//				}


			}
		}
		;
		int startIndex = 0;
		List<List<Id<Link>>> linkLists = new ArrayList<>();

		List<Id<Link>> links = new ArrayList<>();
		links.addAll(linkstoKeep);
		Collections.shuffle(links);
		Random random = MatsimRandom.getRandom();
		double targetSize = sampleRate * (double) links.size();
		while (links.size() > targetSize)
			links.remove(random.nextInt(links.size()));
		while (startIndex < links.size()) {
			linkLists.add(links.subList(startIndex, Math.min(startIndex + 49, links.size())));
			startIndex += 49;
		}
//		new Runner(links).run();

		linkLists.parallelStream().forEach(linkList -> {
			new Runner(linkList).run();
		});

		linkstoKeep.forEach(linkId -> {
			Link link = network.getLinks().get(linkId);
			link.getAttributes().putAttribute(
					"keepLink", true);
			Link linkInOppositeDirection = NetworkUtils.findLinkInOppositeDirection(link);
			if (linkInOppositeDirection != null)
				linkInOppositeDirection.getAttributes().putAttribute(
						"keepLink", true);
		});
	}

}

/**
 * Finds the path between two nodes, favouring travel time and level of OSM hierarchy, with links lower in the
 * hierarchy incurring increasing penalties.
 */
class OSMHierarchyFavouringFastestPathCalculator {
	final Network network;
	final Config config;
	TravelDisutilityFactory disutilityFactory;
	TravelTime travelTime;
	LeastCostPathCalculator dijkstra;

	// define how the travel disutility is computed:

	OSMHierarchyFavouringFastestPathCalculator(Network network, Config config) {
		this.network = network;
		this.config = config;
		travelTime = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
		disutilityFactory = new OSMHierarchyTravelDisutilityFactory();
		dijkstra = new DijkstraFactory().createPathCalculator(network,
				disutilityFactory.createTravelDisutility(travelTime), travelTime
		);
	}

	LeastCostPathCalculator.Path getPath(Node fromNode, Node toNode) {

		return dijkstra.calcLeastCostPath(fromNode, toNode, 0, null, null);
	}

}

/**
 * Helper class to {@link OSMHierarchyFavouringFastestPathCalculator}
 */
class OSMHierarchyTravelDisutilityFactory implements TravelDisutilityFactory {

	@Override
	public TravelDisutility createTravelDisutility(TravelTime timeCalculator) {
		return new OSMHierarchyTravelDisutility(timeCalculator);
	}
}

/**
 * Helper class to {@link OSMHierarchyFavouringFastestPathCalculator}, where a disutility factor is associated with
 * each level of the OSM hierarchy observed in the Duesseldorf scenario.
 * <p>
 * The map of disutilities is pretty arbitrary, tuned after several runs of
 * {@link ExtractMinimalConnectedNetworkFromOSMUtilities#markConnectedLinksOfQualifyingLevelInOSMHierarchy(Network, Config, double, double)}
 * to produce a network providing adequate connection and few orphaned links. May have to be adjusted based on
 * context, and certainly does not contain the entire hierarchy specified in the <a href="">OSM wiki</a>.
 */
class OSMHierarchyTravelDisutility implements TravelDisutility {
	final TravelTime travelTime;
	static Map<String, Double> osmHierarchyMap = new HashMap<>();

	static {
		osmHierarchyMap.put("highway.motorway", 1d);
		osmHierarchyMap.put("highway.motorway_link", 1d);
		osmHierarchyMap.put("highway.trunk", 1.1);
		osmHierarchyMap.put("highway.trunk_link", 1.1);
		osmHierarchyMap.put("highway.primary", 1.21);
		osmHierarchyMap.put("highway.primary_link", 1.21);
		osmHierarchyMap.put("highway.primary|railway.tram", 1.21);
		osmHierarchyMap.put("highway.secondary", 1.331);
		osmHierarchyMap.put("highway.secondary_link", 1.331);
		osmHierarchyMap.put("highway.secondary|railway.tram", 1.331);
		osmHierarchyMap.put("highway.tertiary", 1.4641);
		osmHierarchyMap.put("highway.tertiary|railway.tram", 1.4641);
		osmHierarchyMap.put("highway.unclassified", 1.61);
		osmHierarchyMap.put("highway.residential", 1.772);
		osmHierarchyMap.put("highway.residential|railway.tram", 1.772);
		osmHierarchyMap.put("highway.living_street", 1.949);
		osmHierarchyMap.put("ZZZ", 2.14);
	}

	OSMHierarchyTravelDisutility(TravelTime travelTime) {
		this.travelTime = travelTime;
	}

	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		return getLinkMinimumTravelDisutility(link);
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		double linkTypeCost = getOSMLinkTypeCost(link);
		return travelTime.getLinkTravelTime(link, 0d, null, null) * linkTypeCost;
	}

	static double getOSMLinkTypeCost(Link link) {
		String type = getOSMLinkType(link);
		double linkTypeCost = osmHierarchyMap.get(type);
		return linkTypeCost;
	}

	static String getOSMLinkType(Link link) {
		Object type = link.getAttributes().getAttribute("type");
		return type == null ? "ZZZ" : type.toString();
	}
}

