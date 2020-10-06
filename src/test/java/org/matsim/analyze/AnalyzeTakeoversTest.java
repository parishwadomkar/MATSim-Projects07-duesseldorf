package org.matsim.analyze;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;

import java.util.List;


public class AnalyzeTakeoversTest {

    private AnalyzeTakeovers analyzer;

    @Before
    public void setUp() throws Exception {
        analyzer = new AnalyzeTakeovers();
    }

    @Test
    public void analyze() {

        List<Event> events = List.of(
                new LinkEnterEvent(100, Id.createVehicleId("veh1"), Id.createLinkId("link1"))
        );

        int n = analyzer.analyze(events);

        assert n == 2 : "There must be 2 takeovers";

    }
}