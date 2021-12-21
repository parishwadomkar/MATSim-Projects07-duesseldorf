package org.matsim.analysis;

import com.google.common.collect.Lists;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.HasLinkId;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.run.NetworkCleaner;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ExtractConnectedNetworkFavouringLinkVolume {


	public static void countLinkTraversalsFromEvents(String eventsFile, Network network) {
		Map<Id<Link>, AtomicInteger> linkTraversalCount = new HashMap<>();
		network.getLinks().values().forEach(link -> linkTraversalCount.put(link.getId(), new AtomicInteger(0)));

		BasicEventHandler traversalCounter = new BasicEventHandler() {
			@Override
			public void handleEvent(Event event) {
				if (event instanceof LinkEnterEvent || event instanceof LinkLeaveEvent)
					linkTraversalCount.get(((HasLinkId) event).getLinkId()).incrementAndGet();
			}
		};

		EventsManagerImpl events = new EventsManagerImpl();
		events.addHandler(traversalCounter);
		new MatsimEventsReader(events).readFile(eventsFile);
		int numberOfIrrelevantLinks = linkTraversalCount.values().stream().filter(atomicInteger -> atomicInteger.get() == 0).collect(Collectors.toList()).size();
		System.out.println(String.format("This network has a total of %d links of which %d never appear to be traversed in the events file",
				linkTraversalCount.size(), numberOfIrrelevantLinks));
	}


	public static void countLinkTraversalsFromPopulation(Population population, Network network) {
		Map<Id<Link>, AtomicInteger> linkTraversalCount = new HashMap<>();
		network.getLinks().values().forEach(link -> linkTraversalCount.put(link.getId(), new AtomicInteger(0)));
		Set<String> validModes = new HashSet<>();
		validModes.addAll(Lists.newArrayList(TransportMode.car, TransportMode.ride, "freight"));
		population.getPersons().values().forEach(
				person -> person.getSelectedPlan().getPlanElements().forEach(
						planElement -> {
							if (planElement instanceof Leg) {
								Leg leg = (Leg) planElement;
								if (validModes.contains(leg.getMode())) {
//								if (leg.getMode().equals(TransportMode.car)) {
									NetworkRoute route = (NetworkRoute) leg.getRoute();
									route.getLinkIds().forEach(linkId -> linkTraversalCount.get(linkId).addAndGet(1));
								}
							}
						}));
		linkTraversalCount.entrySet().forEach(linkEntry -> {
			Link link = network.getLinks().get(linkEntry.getKey());
			link.getAttributes().putAttribute("traversalCount", linkEntry.getValue().intValue());
		});
		int numberOfIrrelevantLinks = linkTraversalCount.values().stream().filter(atomicInteger ->
				atomicInteger.get() == 0).collect(Collectors.toList()).size();
		System.out.println(String.format("This network has a total of %d links of which %d never appear to be " +
						"traversed by car or freight in the plans file",
				linkTraversalCount.size(), numberOfIrrelevantLinks));
	}

	/**
	 * For most part, any given network route in a plan will only
	 * see increasing traversalCount on the path elements as it leaves tributaries to join major arterials, then see a
	 * decrease in traversalCount as it leaves arterials to reach its final destination.
	 * <p/>
	 * We want activities to be mapped to the first link with a traversalCount >= <b>minimumTraversalCount</b>, or the
	 * first link lying within the boundary polygon of interest,
	 * set by {@link #networkSpatialJoinToCityBoundaryPolygon(String, Network)}
	 * <p/>
	 * There may be edge cases, but if we extract the largest component, we assume we will anyways re-route without too
	 * much deviation in travel time.
	 * <p>
	 * If an activity sits on a non-qualifying link, move it to the nearest quolifying link.
	 *
	 * @param minimumTraversalCount
	 */
	public static Population mapActivitiesToQualifyingLinks(Network network, int minimumTraversalCount,
															Scenario scenario) {
		Population out = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		PopulationFactory factory = out.getFactory();
		scenario.getPopulation().getPersons().values().forEach(person -> {
			Person newPerson = factory.createPerson(person.getId());
			Plan plan = factory.createPlan();
			Activity lastAct = null;
			Leg lastLeg = null;
			for (PlanElement planElement : person.getSelectedPlan().getPlanElements()) {
				if (planElement instanceof Activity) {
					Activity activity = (Activity) planElement;
					if (!activity.getType().contains("interaction")) {
						if (network.getLinks().get(activity.getLinkId()) == null)
							activity.setLinkId(NetworkUtils.getNearestLink(network, activity.getCoord()).getId());
						plan.addActivity(activity);
					}
				} else {
					Leg leg = (Leg) planElement;
					plan.addLeg(leg);
				}
			}
			newPerson.addPlan(plan);
			newPerson.setSelectedPlan(plan);
			out.addPerson(newPerson);
		});
		return out;
	}

	public static void networkSpatialJoinToCityBoundaryPolygon(String polygonFileName, Network network) {
		Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(new File(polygonFileName).getPath());

		Iterator<SimpleFeature> iterator = features.iterator();
		GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 25832);
		Polygonal polygon = null;
		while (iterator.hasNext()) {
			SimpleFeature feature = iterator.next();
			// TODO: there is only one polygon here, but need to dissolve multiple polygons into a single one
			polygon = (Polygonal) feature.getDefaultGeometry();
		}
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
//		networkSpatialJoinToCityBoundaryPolygon(args[1], inputNetwork);
//		markLinksOnPathToMostCentralLink(inputNetwork, ConfigUtils.createConfig(), 3000,
//				Id.createNodeId(Long.parseLong(args[2])));
//		correctNetworkLinks(inputNetwork);

//
//		Scenario routedScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
//		new PopulationReader(routedScenario).readFile(args[2]);
//		countLinkTraversalsFromPopulation(routedScenario.getPopulation(), inputNetwork);
//
//		new NetworkWriter(inputNetwork).write(args[3]);

//		Network outNet = extractNetwork(20, inputNetwork);
//		new NetworkWriter(outNet).write(args[1]);
		new org.matsim.core.network.algorithms.NetworkCleaner().run(inputNetwork);
//
//		Network cleanNet = NetworkUtils.createNetwork(ConfigUtils.createConfig());
//		new MatsimNetworkReader(cleanNet).readFile(args[2]);
//
		Network newNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
		new MatsimNetworkReader(newNetwork).readFile(args[0]);
//
//		removeLinksFromTargetNetworkNotInSourceNetwork(inputNetwork, newNetwork);
//		removeNonPtLinksAndNodesFromNetwork(newNetwork);
		removeNonPtNodesFromNetwork(newNetwork);
		combineNetworks(inputNetwork, newNetwork);
		;
//		new NetworkWriter(extractNetwork(10000, newNetwork)).write(args[1]);
		new NetworkWriter(newNetwork).write(args[1]);
		BufferedWriter bufferedWriter = IOUtils.getBufferedWriter(args[2]);
		bufferedWriter.write("link,cap\n");
		newNetwork.getLinks().values().forEach(link -> {
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

	public static void correctNetworkLinks(Network network) {
		Map<Id<Link>, ? extends Link> links = network.getLinks();

		// Double lanes for these links
		List<String> incorrectList = Lists.newArrayList(
				"289987955#0",//Breitestrasse / Heinrich Heine Allee links
				"289987955#2",
				"4683309#0",
				"145433835",
				"46146378#2",
				"33381974",
				"621308781#0",
				"145503631#0",
				"149902601",
				"147614221",
				"147614263",
				"420530117", // Kasernstrasse
				"40348110#4",
				"40348110#2",
				"40348110#0",
				"142697893#0",
				"223447139", // Oststrasse
				"-223447139",
				"145424749#0",
				"-145424749#2",
				"149291901#0", // Karl-Rudolfstrasse
				"85388142#0",
				"144531009#0",
				"23157292#0", // Corneliusstrasse
				"207108052#0",
				"219116943#0",
				"239250010#2" // Brunnenstrasse

		);

		//dump it into a set in case we accidentally repeat an id in the list
		Set<String> incorrect = new HashSet<>();
		incorrect.addAll(incorrectList);

		for (String l : incorrect) {
			Link link = links.get(Id.createLinkId(l));
			link.setNumberOfLanes(link.getNumberOfLanes() * 2);
			link.setCapacity(link.getCapacity() * 2);
		}
	}

	public static Network extractNetwork(int minimumTraversals, Network inputNetwork) {
		Network outputNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());

		inputNetwork.getLinks().values().forEach(link -> {
			Object traversalCount = link.getAttributes().getAttribute("traversalCount");
			int count = 0;
			if (traversalCount != null)
				count = (int) traversalCount;
			if (link.getAllowedModes().contains(TransportMode.pt) ||
//					(link.getAttributes().getAttribute("junction") != null) ||
					(link.getAttributes().getAttribute("keepLink") != null) ||
					count >= minimumTraversals) {
				try {
					outputNetwork.addNode(link.getFromNode());
				} catch (IllegalArgumentException e) {

				}
				try {
					outputNetwork.addNode(link.getToNode());

				} catch (IllegalArgumentException e) {

				}
				outputNetwork.addLink(link);
				Link linkInOppositeDirection = NetworkUtils.findLinkInOppositeDirection(link);
				try {
					if (linkInOppositeDirection != null)
						outputNetwork.addLink(linkInOppositeDirection);
				} catch (IllegalArgumentException e) {

				}
			}
		});
		return outputNetwork;
	}

	public static void removeLinksFromTargetNetworkNotInSourceNetwork(Network source, Network target) {
		List<Link> badLinks =
				target.getLinks().values().stream().filter(link -> {
					if (link.getAllowedModes().contains(TransportMode.pt))
						return false;

					return source.getLinks().get(link.getId()) == null;
				}).collect(Collectors.toList());
		badLinks.forEach(link -> target.removeLink(link.getId()));
		List<Id<Node>> badNodes = new ArrayList<>();
		target.getNodes().values().forEach(node -> {
			if (node.getInLinks().size() == 0 || node.getOutLinks().size() == 0) badNodes.add(node.getId());
		});
		badNodes.forEach(node -> target.removeNode(node));

	}

	public static void combineNetworks(Network source, Network target) {

		source.getNodes().values().forEach(nn -> {
			try {
				target.addNode(nn);
			} catch (IllegalArgumentException e) {
			}
		});
		source.getLinks().values().forEach(ll -> {
			try {
				target.addLink(ll);
			} catch (IllegalArgumentException e) {
			}
		});

	}

	public static void removeNonPtNodesFromNetwork(Network target) {

		List<Node> badNodes =
				new ArrayList<>(target.getNodes().values().stream().filter(node -> !node.getId().toString().startsWith("pt")).collect(Collectors.toList()));
		badNodes.forEach(node -> target.removeNode(node.getId()));

	}
	public static void removeNonPtLinksAndNodesFromNetwork(Network target) {
		List<Link> badLinks =
				target.getLinks().values().stream().filter(link -> !link.getAllowedModes().contains(TransportMode.pt)).collect(Collectors.toList());
		badLinks.forEach(link -> target.removeLink(link.getId()));
		List<? extends Node> badNodes = target.getNodes().values().stream().filter(node -> node.getInLinks().size() == 0 && node.getOutLinks().size() == 0).collect(Collectors.toList());
		badNodes.forEach(node -> target.removeNode(node.getId()));

	}

	public static void markLinksOnPathToMostCentralLink(Network network, Config config, double minimumCapacity,
														Id<Node> centreNode) {
		Node node = network.getNodes().get(centreNode);
		Set<Id<Link>> linkstoKeep = new ConcurrentSkipListSet<>();
		AtomicInteger i = new AtomicInteger(0);

		network.getLinks().values().stream().filter(link -> link.getCapacity() >= minimumCapacity && !link.getAllowedModes().contains(TransportMode.pt)).forEach(link -> linkstoKeep.add(link.getId()));


		class Runner implements Runnable {

			FastFatPathCalculator pathCalculator = new FastFatPathCalculator(network, config);
			final List<Id<Link>> myLinks;

			Runner(List<Id<Link>> myLinks) {
				this.myLinks = myLinks;
			}

			@Override
			public void run() {
				myLinks.forEach(link -> {
					pathCalculator.getPath(network.getLinks().get(link).getToNode(), node).links.forEach(pathLink -> {
						linkstoKeep.add(pathLink.getId());
					});
					pathCalculator.getPath(node, network.getLinks().get(link).getFromNode()).links.forEach(pathLink -> {
						linkstoKeep.add(pathLink.getId());
					});
					System.out.print(i.get() + ":" + linkstoKeep.size() + "..." + (i.incrementAndGet() % 20 == 0 ? "\n" :
							""));

				});

			}
		}
		;
		int startIndex = 0;
		List<List<Id<Link>>> linkLists = new ArrayList<>();

		List<Id<Link>> links = new ArrayList<>();
		links.addAll(linkstoKeep);
		while (startIndex < links.size()) {
			linkLists.add(links.subList(startIndex, Math.min(startIndex + 999, links.size())));
			startIndex += 999;
		}
		linkLists.parallelStream().forEach(linkList -> {
			new Runner(linkList).run();
		});

		linkstoKeep.forEach(link -> network.getLinks().get(link).getAttributes().putAttribute(
				"keepLink", true));
	}

}

class FastFatPathCalculator {
	final Network network;
	final Config config;
	TravelDisutilityFactory disutilityFactory;
	TravelTime travelTime;
	LeastCostPathCalculator dijkstra;

	// define how the travel disutility is computed:

	FastFatPathCalculator(Network network, Config config) {
		this.network = network;
		this.config = config;
		travelTime = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
		disutilityFactory = new OnlyTimeDependentTravelDisutilityFactory();
		dijkstra = new DijkstraFactory().createPathCalculator(network,
				disutilityFactory.createTravelDisutility(travelTime), travelTime
		);
	}

	LeastCostPathCalculator.Path getPath(Node fromNode, Node toNode) {

		return dijkstra.calcLeastCostPath(fromNode, toNode, 0, null, null);
	}

}

class HighSpeedHighCapacityFavouringTravelDisutilityFactory implements TravelDisutilityFactory {

	@Override
	public TravelDisutility createTravelDisutility(TravelTime timeCalculator) {
		return null;
	}
}

class CapacityFavouringTravelDisutility implements TravelDisutility {
	final TravelTime travelTime;

	CapacityFavouringTravelDisutility(TravelTime travelTime) {
		this.travelTime = travelTime;
	}

	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		return getLinkMinimumTravelDisutility(link);
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return travelTime.getLinkTravelTime(link, 0d, null, null) / link.getCapacity();
	}
}
