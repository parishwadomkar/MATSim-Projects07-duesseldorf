package org.matsim.analysis;

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
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.run.NetworkCleaner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ExtractConnectedNetworkFavouringLinkVolume {
	private Scenario scenario;
	Map<Id<Link>, AtomicInteger> linkTraversalCount = new HashMap<>();

	public ExtractConnectedNetworkFavouringLinkVolume(String network, String transitSchedule, String population) {
		this(network);
//		weightTransitLinks(transitSchedule);
		int numberOfIrrelevantLinks = linkTraversalCount.values().stream().filter(atomicInteger -> atomicInteger.get() == 0).collect(Collectors.toList()).size();

		System.out.println(String.format("This network has a total of %d links of which %d have no priority indication or appear in the transit file",
				linkTraversalCount.size(), numberOfIrrelevantLinks));

		weightByLinkTraversals(population);
		numberOfIrrelevantLinks = linkTraversalCount.values().stream().filter(atomicInteger -> atomicInteger.get() == 0).collect(Collectors.toList()).size();

		System.out.println(String.format("This network has a total of %d links of which %d have no priority indication or appear in the transit or population file",
				linkTraversalCount.size(), numberOfIrrelevantLinks));
	}

	public ExtractConnectedNetworkFavouringLinkVolume(String network, String eventsFile) {
		this(network);
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
		System.out.println(String.format("This network has a total of %d links of which %d have no priority indication or appear in the events file",
				linkTraversalCount.size(), numberOfIrrelevantLinks));
	}

	public ExtractConnectedNetworkFavouringLinkVolume(String network) {
		scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(network);
		scenario.getNetwork().getLinks().values().forEach(link -> linkTraversalCount.put(link.getId(), new AtomicInteger(0)));
//		weightJunctions();
		int numberOfIrrelevantLinks = linkTraversalCount.values().stream().filter(atomicInteger -> atomicInteger.get() == 0).collect(Collectors.toList()).size();
		System.out.println(String.format("This network has a total of %d links of which %d have no priority indication",
				linkTraversalCount.size(), numberOfIrrelevantLinks));

	}

	private void weightByLinkTraversals(String population) {
		new PopulationReader(scenario).readFile(population);
		scenario.getPopulation().getPersons().values().forEach(
				person -> person.getSelectedPlan().getPlanElements().forEach(
						planElement -> {
							if (planElement instanceof Leg) {
								Leg leg = (Leg) planElement;
								if (leg.getMode().equals(TransportMode.car)) {
									NetworkRoute route = (NetworkRoute) leg.getRoute();
									route.getLinkIds().forEach(linkId -> linkTraversalCount.get(linkId).addAndGet(1));
								}
							}
						}));
	}

	private void weightJunctions() {
		scenario.getNetwork().getLinks().values().forEach(link -> {
			Object junction = link.getAttributes().getAttribute("junction");
			if (junction != null) {
				linkTraversalCount.get(link.getId()).incrementAndGet();
			}
		});
//		scenario.getNetwork().getNodes().values().forEach(node -> {
//			Object type = node.getAttributes().getAttribute("type");
//			if (type != null) {
//				node.getInLinks().values().forEach(link -> linkTraversalCount.get(link.getId()).addAndGet(100));
//				node.getOutLinks().values().forEach(link -> linkTraversalCount.get(link.getId()).addAndGet(100));
//			}
//		});
	}

//	private void weightTransitLinks(String transitSchedule) {
//		new TransitScheduleReader(scenario).readFile(transitSchedule);
//		scenario.getTransitSchedule().getTransitLines().values().forEach(
//				transitLine -> transitLine.getRoutes().values().forEach(
//						transitRoute -> {
//							transitRoute.getRoute().getLinkIds().forEach(linkId -> scenario.getNetwork().getLinks().get(linkId).getAttributes().putAttribute("transitlink"));
//
//						}));
//	}

	public static void main(String[] args) {
		ExtractConnectedNetworkFavouringLinkVolume extraction = new ExtractConnectedNetworkFavouringLinkVolume(args[0], args[1], args[2]);
		Network outNet = extraction.extractNetwork(10);
		new NetworkWriter(outNet).write(args[3]);
		new NetworkCleaner().run(args[3],args[4]);
//		new NetworkWriter(extraction.scenario.getNetwork()).write(args[5]);
	}

	private Network extractNetwork(int minimumTraversals) {
		Network outputNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());

		linkTraversalCount.entrySet().forEach(linkEntry -> {
			if (linkEntry.getValue().get() >= minimumTraversals) {
				Link link = scenario.getNetwork().getLinks().get(linkEntry.getKey());
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
				try{
				if(linkInOppositeDirection!=null)
					outputNetwork.addLink(linkInOppositeDirection);
				} catch (IllegalArgumentException e) {

				}
				link.getAttributes().putAttribute("traversalCount", linkEntry.getValue().intValue());


			}
		});
		scenario.getNetwork().getLinks().values().forEach(link -> {
			if(link.getAllowedModes().contains(TransportMode.pt)||(link.getAttributes().getAttribute("junction")!=null)){
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
				try{
					if(linkInOppositeDirection!=null)
						outputNetwork.addLink(linkInOppositeDirection);
				} catch (IllegalArgumentException e) {

				}
			}
		});
		return outputNetwork;
	}

}
