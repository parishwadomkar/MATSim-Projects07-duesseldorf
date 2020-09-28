package org.matsim.analyze;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LaneEnterEvent;
import org.matsim.core.api.experimental.events.LaneLeaveEvent;
import org.matsim.core.api.experimental.events.handler.LaneEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LaneLeaveEventHandler;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.Lane;
import org.matsim.prepare.GetCountData;
import org.matsim.prepare.NodeMatcher;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Map<Id<Link>, List<Event>> links = new HashMap<>();

        for (int i = 0; i < events.size(); i++){

            Id<Link> linkTemp = Id.createLinkId(events.get(i).getAttributes().get("link"));
            Event eventTemp = events.get(i);

            if(!links.keySet().contains(linkTemp)) {
                links.put(linkTemp, new ArrayList<Event>());
            }
            links.get(linkTemp).add(eventTemp);
        }

        for (List<Event> event : links.values()) {
            int temp = 0;
            for (int i = 0; i < event.size(); i++) {
                for (int j = 0; j < event.size(); j++) {
                    for (int k = 0; k < event.size(); k++) {
                        for (int l = 0; l < event.size(); l++) {
                            checkLinkForOvertake(event.get(i), event.get(j), event.get(k), event.get(l));
                        }
                    }
                }
            }
            checkedEvents = checkedEvents + event.size();
            log.info("Checked link " + event.get(temp).getAttributes().get("link") + " with " + event.size() + " events.");
            log.info("Checked " + checkedEvents + "/" + events.size());
            temp++;
        }

        return 0;
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
