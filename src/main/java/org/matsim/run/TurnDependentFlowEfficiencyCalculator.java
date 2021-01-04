package org.matsim.run;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.mobsim.qsim.qnetsimengine.QVehicle;
import org.matsim.core.mobsim.qsim.qnetsimengine.flow_efficiency.FlowEfficiencyCalculator;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

public class TurnDependentFlowEfficiencyCalculator implements FlowEfficiencyCalculator {

	/**
	 * Attribute name for turn efficiency parameter.
	 */
	public static final String ATTR_TURN_EFFICIENCY = "turnEfficiency";

	/**
	 * Maps from link to link to turn efficiencies.
	 */
	private final Long2DoubleMap linkEfficiencies = new Long2DoubleOpenHashMap();

	/**
	 * Maps from lane to link to turn efficiency.
	 */
	private final Long2DoubleMap laneEfficiencies = new Long2DoubleOpenHashMap();

	/**
	 * Contains links that have turn efficiencies, true if also for each lane.
	 */
	private final Map<Link, Boolean> hasLaneEfficiency = new IdentityHashMap<>();

	@Inject
	public TurnDependentFlowEfficiencyCalculator(Scenario scenario) {

		for (Link link : scenario.getNetwork().getLinks().values()) {

			Map<String, String> turnEfficiency = (Map<String, String>) link.getAttributes().getAttribute(ATTR_TURN_EFFICIENCY);
			if (turnEfficiency != null) {

				hasLaneEfficiency.put(link, false);

				for (Map.Entry<String, String> e : turnEfficiency.entrySet()) {
					Id<Link> toLink = Id.createLinkId(e.getKey());
					double f = Double.parseDouble(e.getValue());
					linkEfficiencies.put(key(link.getId(), toLink), f);
				}
			}

		}

		for (Map.Entry<Id<Link>, LanesToLinkAssignment> l2l : scenario.getLanes().getLanesToLinkAssignments().entrySet()) {

			Link link = scenario.getNetwork().getLinks().get(l2l.getKey());

			for (Lane lane : l2l.getValue().getLanes().values()) {
				Map<String, String> turnEfficiency = (Map<String, String>) lane.getAttributes().getAttribute(ATTR_TURN_EFFICIENCY);
				if (turnEfficiency != null) {
					hasLaneEfficiency.put(link, true);

					for (Map.Entry<String, String> e : turnEfficiency.entrySet()) {
						Id<Link> toLink = Id.createLinkId(e.getKey());
						double f = Double.parseDouble(e.getValue());
						laneEfficiencies.put(key(lane.getId(), toLink), f);
					}
				}
			}
		}
	}

	@Override
	public double calculateFlowEfficiency(QVehicle qVehicle, @Nullable QVehicle previousQVehicle, @Nullable Double timeGapToPreviousVeh, Link link, Id<Lane> laneId) {

		Boolean laneEfficiency = hasLaneEfficiency.get(link);
		// no turn efficiency known
		if (laneEfficiency == null)
			return 1.0;

		Id<Link> toLink = qVehicle.getDriver().chooseNextLinkId();

		if (toLink == null)
			return 1.0;

		if (laneEfficiency) {
			return laneEfficiencies.getOrDefault(key(laneId, toLink), 1);
		} else {
			return linkEfficiencies.getOrDefault(key(link.getId(), toLink), 1);
		}
	}


	/**
	 * 64bit compound key of two ids.
	 */
	private static long key(Id<?> a, Id<?> b) {
		return ((long) a.index() << 32) | ((long) b.index() & 0xFFFF_FFFFL);
	}
}
