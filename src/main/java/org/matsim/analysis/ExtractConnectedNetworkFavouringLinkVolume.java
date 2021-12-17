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
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.run.NetworkCleaner;
import org.matsim.run.XY2Links;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.*;
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
			person.getSelectedPlan().getPlanElements().forEach(planElement -> {
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
			});
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
				node.getInLinks().values().forEach(link -> link.getAttributes().putAttribute("insideBoundary", true));
				node.getOutLinks().values().forEach(link -> link.getAttributes().putAttribute("insideBoundary", true));
			}
		});
		int numberOfIrrelevantLinks = network
				.getLinks()
				.values()
				.stream()
				.filter(link -> {
					Object insideBoundary = link.getAttributes().getAttribute("insideBoundary");
					return insideBoundary != null && (boolean) insideBoundary;
				})
				.collect(Collectors.toList())
				.size();
		System.out.println(String.format("This network has a total of %d links of which %d appear inside the city polygon boundary",
				network.getLinks().size(), numberOfIrrelevantLinks));
	}


	public static void main(String[] args) {
		Network inputNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
		new MatsimNetworkReader(inputNetwork).readFile(args[0]);

//		correctNetworkLinks(inputNetwork);

//		networkSpatialJoinToCityBoundaryPolygon(args[1], inputNetwork);
//
//		Scenario routedScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
//		new PopulationReader(routedScenario).readFile(args[2]);
//		countLinkTraversalsFromPopulation(routedScenario.getPopulation(), inputNetwork);
//
//		new NetworkWriter(inputNetwork).write(args[3]);

		Network outNet = extractNetwork(100, inputNetwork);
		new NetworkWriter(outNet).write(args[1]);
		new NetworkCleaner().run(args[1], args[2]);

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
					(link.getAttributes().getAttribute("junction") != null) ||
					(link.getAttributes().getAttribute("insideBoundary") != null) ||
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

}
