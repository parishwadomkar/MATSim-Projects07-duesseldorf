package org.matsim.analyze;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;

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
                new LinkEnterEvent(2000, Id.createVehicleId("587631874"), Id.createLinkId("1124543612")),
                new LinkLeaveEvent(2010, Id.createVehicleId("587631874"), Id.createLinkId("1124543612")),

                new LinkEnterEvent(2002, Id.createVehicleId("64371864532"), Id.createLinkId("1124543612")),
                new LinkLeaveEvent(2008, Id.createVehicleId("64371864532"), Id.createLinkId("1124543612")),

                new LinkEnterEvent(5000, Id.createVehicleId("613746138"), Id.createLinkId("2138946719241")),
                new LinkLeaveEvent(5050, Id.createVehicleId("613746138"), Id.createLinkId("2138946719241")),
                new LinkLeaveEvent(7000, Id.createVehicleId("613746138"), Id.createLinkId("2138946719241")),

                new LinkEnterEvent(5100, Id.createVehicleId("642913864913"), Id.createLinkId("2138946719241")),
                new LinkLeaveEvent(5120, Id.createVehicleId("642913864913"), Id.createLinkId("2138946719241"))
        );

        int n = analyzer.analyze(events);

        assert n == 1 : "There must be 1 takeovers";

    }
}