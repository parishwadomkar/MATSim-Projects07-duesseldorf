package org.matsim.analysis;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LaneEnterEvent;
import org.matsim.core.api.experimental.events.LaneLeaveEvent;
import org.matsim.core.api.experimental.events.handler.LaneEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LaneLeaveEventHandler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.lanes.Lane;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;


@CommandLine.Command(
        name = "analyzeTakeovers",
        description = "Analyze takeover of vehicles on one link, but different lanes."
)
public class AnalyzeTakeovers implements Callable<Integer>, LinkEnterEventHandler, LinkLeaveEventHandler, LaneEnterEventHandler, LaneLeaveEventHandler {

    private static final Logger log = LogManager.getLogger(AnalyzeTakeovers.class);

    @CommandLine.Parameters(arity = "1", paramLabel = "INPUT", description = "Input event file")
    private Path input;

    private List<Event> events;
    private int countOvertakes = 0;

    public static void main(String[] args) {
        System.exit(new CommandLine(new AnalyzeTakeovers()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        events = new ArrayList<>();

        EventsManager manager = new EventsManagerImpl();
        manager.addHandler(this);
        manager.initProcessing();

        MatsimEventsReader reader = new MatsimEventsReader(manager);

        reader.addCustomEventMapper(LaneLeaveEvent.EVENT_TYPE, event ->
                new LaneLeaveEvent(
                        event.getTime(),
                        Id.createVehicleId(event.getAttributes().get(LaneLeaveEvent.ATTRIBUTE_VEHICLE)),
                        Id.createLinkId(event.getAttributes().get(LaneLeaveEvent.ATTRIBUTE_LINK)),
                        Id.create(event.getAttributes().get(LaneLeaveEvent.ATTRIBUTE_LANE), Lane.class)
                )
        );

        reader.addCustomEventMapper(LaneEnterEvent.EVENT_TYPE, event ->
                new LaneEnterEvent(
                        event.getTime(),
                        Id.createVehicleId(event.getAttributes().get(LaneEnterEvent.ATTRIBUTE_VEHICLE)),
                        Id.createLinkId(event.getAttributes().get(LaneEnterEvent.ATTRIBUTE_LINK)),
                        Id.create(event.getAttributes().get(LaneEnterEvent.ATTRIBUTE_LANE), Lane.class)
                )
        );


        reader.readFile(input.toString());
        manager.finishProcessing();

        log.info("Read {} events", events.size());

        analyze(events);

        return 0;
    }

    /**
     * Analyze events for takeover maneuver
     *
     * @return number of takeovers
     */
    int analyze(List<Event> events) {

        Map<Id<Link>, List<LinkEnterEvent>> linkEntered = new HashMap<>();
        Map<Id<Link>, List<LinkLeaveEvent>> linkLeave = new HashMap<>();
        Map<Id<Link>, List<Event>> laneEvents = new HashMap<>();

        // maximum time for one vehicle on a link
        double timeThreshold = 300;

        for (Event value : events) {

            Id<Link> linkTemp = Id.createLinkId(value.getAttributes().get("link"));

            if (linkTemp.toString().startsWith("pt_"))
                continue;
            if (value instanceof LinkEnterEvent)
                linkEntered.computeIfAbsent(linkTemp, (k) -> new ArrayList<>()).add((LinkEnterEvent) value);
            else if (value instanceof LinkLeaveEvent)
                linkLeave.computeIfAbsent(linkTemp, (k) -> new ArrayList<>()).add((LinkLeaveEvent) value);
            else if (value instanceof LaneEnterEvent || value instanceof LaneLeaveEvent)
                laneEvents.computeIfAbsent(linkTemp, (k) -> new ArrayList<>()).add(value);
        }

        linkEntered.values().forEach(l -> l.sort(Comparator.comparingDouble(Event::getTime)));
        linkLeave.values().forEach(l -> l.sort(Comparator.comparingDouble(Event::getTime)));

        Map<Id<Link>, List<Pair<LinkEnterEvent, LinkLeaveEvent>>> linkPairs = new HashMap<>();

        log.info("Total amount of links: " + linkEntered.keySet().size());

        for (Map.Entry<Id<Link>, List<LinkEnterEvent>> e : linkEntered.entrySet()) {

            Id<Link> link = e.getKey();

            for (LinkEnterEvent linkEnterEvent : e.getValue()) {

                LinkLeaveEvent linkLeaveEvent = new LinkLeaveEvent(0, Id.createVehicleId(""), Id.createLinkId(""));

                try {
                    for (LinkLeaveEvent otherLinkLeave : linkLeave.get(link)) {
                        if (otherLinkLeave.getVehicleId().equals(linkEnterEvent.getVehicleId())) {
                            if (linkLeaveEvent.getAttributes().get("vehicle").equals("") && otherLinkLeave.getTime() > linkEnterEvent.getTime()) {
                                linkLeaveEvent = otherLinkLeave;
                            } else {
                                if (linkLeaveEvent.getTime() > otherLinkLeave.getTime()) {
                                    if (otherLinkLeave.getTime() > linkEnterEvent.getTime()) {
                                        linkLeaveEvent = otherLinkLeave;
                                    }
                                }
                            }
                        }
                    }
                } catch (NullPointerException ignored) {
                }

                for (LinkEnterEvent linkEnterEventTwo : e.getValue()) {
                    if(linkEnterEvent.getVehicleId().equals(linkEnterEventTwo.getVehicleId())) {
                        if (linkEnterEventTwo.getTime() > linkEnterEvent.getTime() && linkEnterEventTwo.getTime() < linkLeaveEvent.getTime()) {
                            linkLeaveEvent.setTime(0);
                        }
                    }
                }

                if (linkLeaveEvent.getTime() != 0) {
                    if ((linkLeaveEvent.getTime() - linkEnterEvent.getTime()) < timeThreshold) {
                        linkPairs.computeIfAbsent(link, (k) -> new ArrayList<>())
                                .add(Pair.of(linkEnterEvent, linkLeaveEvent));
                    }
                }
            }
        }

        log.info("Added all pairs!");

        linkPairs.entrySet().parallelStream().forEach(e -> {
            List<Pair<LinkEnterEvent, LinkLeaveEvent>> pairs = e.getValue();

            for (int i = 0; i < pairs.size(); i++) {
                for (Pair<LinkEnterEvent, LinkLeaveEvent> pair : pairs) {
                    checkLinkForTakeovers(pairs.get(i), pair, laneEvents.get(pair.getLeft().getLinkId()));
                }
            }
        });

        log.info("Checked all pairs for overtake!");
        log.info("########################## " + countOvertakes + " Overtakes were found ##########################");

        return countOvertakes;
    }

    private void checkLinkForTakeovers(Pair<LinkEnterEvent, LinkLeaveEvent> p1, Pair<LinkEnterEvent, LinkLeaveEvent> p2, List<Event> laneEvents) {
        if (p1.getLeft().getTime() < p2.getLeft().getTime() && p1.getRight().getTime() > p2.getRight().getTime()) {
            log.info("################################ Found Overtake ################################");
            log.info("Vehicle " + p2.getLeft().getVehicleId() + " has overtaken vehicle " +
                    p1.getLeft().getVehicleId() + " on the link " + p1.getLeft().getLinkId() + ".");
            log.info("Enter Event 1: " + p1.getLeft().getTime());
            log.info("Leave Event 1: " + p1.getRight().getTime());
            log.info("Enter Event 2: " + p2.getLeft().getTime());
            log.info("Leave Event 2: " + p2.getRight().getTime());

            try {
                for (Event laneEvent : laneEvents) {

                    if (laneEvent.getTime() == p1.getLeft().getTime()) {
                        log.info("Event 1 lane: " + laneEvent.getAttributes().get("lane"));
                    }

                    if (laneEvent.getTime() == p2.getLeft().getTime()) {
                        log.info("Event 2 lane: " + laneEvent.getAttributes().get("lane"));
                    }
                }
            } catch (NullPointerException ignored) {}

            countOvertakes += 1;

        }
    }

    @Override
    public void handleEvent(LinkEnterEvent linkEnterEvent) {
        events.add(linkEnterEvent);
    }

    @Override
    public void handleEvent(LinkLeaveEvent linkLeaveEvent) {
        events.add(linkLeaveEvent);
    }

    @Override
    public void handleEvent(LaneEnterEvent laneEnterEvent) {
        events.add(laneEnterEvent);
    }

    @Override
    public void handleEvent(LaneLeaveEvent laneLeaveEvent) {
        events.add(laneLeaveEvent);
    }
}
