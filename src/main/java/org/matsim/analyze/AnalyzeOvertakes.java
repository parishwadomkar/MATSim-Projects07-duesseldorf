package org.matsim.analyze;

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

import static java.lang.StrictMath.abs;

/**
 * TODO: documentation
 */
@CommandLine.Command(
        name = "analyzeOvertakes",
        description = "TODO"
)
public class AnalyzeOvertakes implements Callable<Integer>, LinkEnterEventHandler, LinkLeaveEventHandler, LaneEnterEventHandler, LaneLeaveEventHandler {

    private static final Logger log = LogManager.getLogger(AnalyzeOvertakes.class);

    @CommandLine.Parameters(arity = "1", paramLabel = "INPUT", description = "Input event file")
    private Path input;

    private List<Event> events;
    private int countOvertakes  = 0;

    public static void main(String[] args) {
        System.exit(new CommandLine(new AnalyzeOvertakes()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        /*
        2020-10-03T16:00:57,356  INFO AnalyzeOvertakes:201 Event 1
        2020-10-03T16:00:57,356  INFO AnalyzeOvertakes:202 	<event time="58741.0" type="entered link" link="pt_12340" vehicle="tr_21546"  />
        2020-10-03T16:00:57,356  INFO AnalyzeOvertakes:203 	<event time="58921.0" type="left link" link="pt_12340" vehicle="tr_21546"  />
        2020-10-03T16:00:57,356  INFO AnalyzeOvertakes:204 Event 2
        2020-10-03T16:00:57,356  INFO AnalyzeOvertakes:205 	<event time="58745.0" type="entered link" link="pt_12340" vehicle="tr_22984"  />
        2020-10-03T16:00:57,356  INFO AnalyzeOvertakes:206 	<event time="58863.0" type="left link" link="pt_12340" vehicle="tr_22984"  />
        */

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

        Map<Id<Link>, List<LinkEnterEvent>> linkEntered = new HashMap<>();
        Map<Id<Link>, List<LinkLeaveEvent>> linkLeave = new HashMap<>();

        for (Event value : events) {

            Id<Link> linkTemp = Id.createLinkId(value.getAttributes().get("link"));

            if (value instanceof LinkEnterEvent)
                linkEntered.computeIfAbsent(linkTemp, (k) -> new ArrayList<>()).add((LinkEnterEvent) value);
            else if (value instanceof LinkLeaveEvent)
                linkLeave.computeIfAbsent(linkTemp, (k) -> new ArrayList<>()).add((LinkLeaveEvent) value);

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
                        if (otherLinkLeave.getAttributes().get("vehicle").equals(linkEnterEvent.getAttributes().get("vehicle"))) {
                            if (linkLeaveEvent.getAttributes().get("vehicle").equals("") && otherLinkLeave.getTime() > linkEnterEvent.getTime()) {
                                linkLeaveEvent = otherLinkLeave;
                            } else {
                                if (Double.parseDouble(linkLeaveEvent.getAttributes().get("time")) > Double.parseDouble(otherLinkLeave.getAttributes().get("time"))) {
                                    if (Double.parseDouble(otherLinkLeave.getAttributes().get("time")) > Double.parseDouble(linkEnterEvent.getAttributes().get("time"))) {
                                        linkLeaveEvent = otherLinkLeave;
                                    }
                                }
                            }
                        }
                    }
                } catch (NullPointerException ignored) {
                }

                if (linkLeaveEvent.getTime() != 0) {
                    linkPairs.computeIfAbsent(link, (k) -> new ArrayList<>())
                            .add(Pair.of(linkEnterEvent, linkLeaveEvent));
                }
            }
        }

        log.info("Added all pairs!");

        for (Map.Entry<Id<Link>, List<Pair<LinkEnterEvent, LinkLeaveEvent>>> e : linkPairs.entrySet()) {

            List<Pair<LinkEnterEvent, LinkLeaveEvent>> pairs = e.getValue();

            for (int i = 0; i < pairs.size(); i++) {
                for (Pair<LinkEnterEvent, LinkLeaveEvent> pair : pairs) {
                    checkLinkForOvertake(pairs.get(i), pair);
                }
            }
        }

        log.info("Checked all pairs for overtake!");
        log.info("########################## " + countOvertakes + " Overtakes were found ##########################");

        return 0;
    }

    private void checkLinkForOvertake(Pair<LinkEnterEvent, LinkLeaveEvent> p1, Pair<LinkEnterEvent, LinkLeaveEvent> p2) {
        if (p1.getLeft().getTime() < p2.getLeft().getTime() && p1.getRight().getTime() > p2.getRight().getTime()) {
            log.info("################################ Found Overtake ################################");
            log.info("Vehicle " + p2.getLeft().getAttributes().get("vehicle") + " has overtaken vehicle " +
                    p1.getLeft().getAttributes().get("vehicle") + " on the link " + p1.getLeft().getAttributes().get("link") + ".");
            log.info("Enter Event 1: " + p1.getLeft().getTime());
            log.info("Leave Event 1: " + p1.getRight().getTime());
            log.info("Enter Event 2: " + p2.getLeft().getTime());
            log.info("Leave Event 2: " + p2.getRight().getTime());
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
