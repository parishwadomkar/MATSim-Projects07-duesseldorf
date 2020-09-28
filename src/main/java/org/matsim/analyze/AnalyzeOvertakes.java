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

    public static void main(String[] args) {
        System.exit(new CommandLine(new AnalyzeOvertakes()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        events = new ArrayList<>();
        int checkedEvents = 0;

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

        // sort all events by time
        linkEntered.values().forEach(l -> l.sort(Comparator.comparingDouble(Event::getTime)));
        linkLeave.values().forEach(l -> l.sort(Comparator.comparingDouble(Event::getTime)));

        Map<Id<Link>, List<Pair<LinkEnterEvent, LinkLeaveEvent>>> linkPairs = new HashMap<>();

        // TODO: generate link pairs
        for (Map.Entry<Id<Link>, List<LinkEnterEvent>> e : linkEntered.entrySet()) {

            Id<Link> link = e.getKey();

            for (LinkEnterEvent linkEnterEvent : e.getValue()) {

                //  TODO find link leave event
                LinkLeaveEvent linkLeaveEvent = null;

                for (LinkLeaveEvent otherLinkLeave : linkLeave.get(link)) {
                    // TODO: compare time and vehicle
                }


                linkPairs.computeIfAbsent(link, (k) -> new ArrayList<>())
                        .add(Pair.of(linkEnterEvent, linkLeaveEvent));
            }
        }


        for (Map.Entry<Id<Link>, List<Pair<LinkEnterEvent, LinkLeaveEvent>>> e : linkPairs.entrySet()) {

            //Id<Link> link = e.getKey();
            List<Pair<LinkEnterEvent, LinkLeaveEvent>> pairs = e.getValue();

            for (int i = 0; i < pairs.size(); i++) {
                for (int j = 0; j < pairs.size(); j++) {
                    checkLinkForOvertake(pairs.get(i), pairs.get(j));
                }
            }

            //log.info("Checked link " + event.get(temp).getAttributes().get("link") + " with " + event.size() + " events.");
            //log.info("Checked " + checkedEvents + "/" + this.events.size());
        }

        return 0;
    }

    private void checkLinkForOvertake(Pair<LinkEnterEvent, LinkLeaveEvent> p1, Pair<LinkEnterEvent, LinkLeaveEvent> p2) {
        // TODO: check p1 link entered < p2 link entered && p1 link leave > p2 link leave
        // TODO: logging
    }

    private void checkLinkForOvertake(Event eventOneEnter, Event eventTwoEnter, Event eventOneLeft, Event eventTwoLeft) {
        double diff = abs(eventOneEnter.getTime() - eventTwoEnter.getTime());
        double diffOne = eventOneLeft.getTime() - eventOneEnter.getTime();
        double diffTwo = eventTwoLeft.getTime() - eventTwoEnter.getTime();
        if (diff < 100 && diffOne < 10000 && diffTwo < 10000 && diffOne > 0 && diffTwo > 0) {
            if(eventOneEnter.getAttributes().get("type").equals("entered link") && eventOneLeft.getAttributes().get("type").equals("left link") && eventTwoEnter.getAttributes().get("type").equals("entered link") && eventTwoLeft.getAttributes().get("type").equals("left link")) {
                if(eventOneEnter.getAttributes().get("vehicle").equals(eventOneLeft.getAttributes().get("vehicle")) && eventTwoEnter.getAttributes().get("vehicle").equals(eventTwoLeft.getAttributes().get("vehicle"))) {
                    if(eventOneEnter.getTime() < eventTwoEnter.getTime() && eventOneLeft.getTime() > eventTwoLeft.getTime()) {
                        log.error("Found Overtake on link " + eventOneEnter.getAttributes().get("link"));
                        log.info(eventOneEnter.getAttributes());
                        log.info(eventOneLeft.getAttributes());
                        log.info(eventTwoEnter.getAttributes());
                        log.info(eventTwoLeft.getAttributes());
                    }
                }
            }
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
