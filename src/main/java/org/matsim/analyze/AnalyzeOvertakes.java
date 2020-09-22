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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

        // TODO: analyze
        Map<Id<Link>, List<Event>> links;

        return 0;
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
