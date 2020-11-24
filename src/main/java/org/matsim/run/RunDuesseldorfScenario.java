package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.signals.otfvis.OTFVisWithSignalsLiveModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.prepare.*;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
		header = ":: Open Düsseldorf Scenario ::",
		version = RunDuesseldorfScenario.VERSION
)
@MATSimApplication.Prepare({CreateNetwork.class, CreateTransitSchedule.class, PreparePopulation.class, CreateCityCounts.class,
		ExtractEvents.class, CreateBAStCounts.class})
public class RunDuesseldorfScenario extends MATSimApplication {

	/**
	 * Current version identifier.
	 */
	public static final String VERSION = "v1.0";

	/**
	 * Default coordinate system of the scenario.
	 */
	public static final String COORDINATE_SYSTEM = "EPSG:25832";

	/**
	 * 6.00° - 7.56°
	 */
	public static final double[] X_EXTENT = new double[]{290_000.00, 400_000.0};
	/**
	 * 50.60 - 51.65°
	 */
	public static final double[] Y_EXTENT = new double[]{5_610_000.00, 5_722_000.00};

	@CommandLine.Option(names = "--otfvis", defaultValue = "false", description = "Enable OTFVis live view")
	private boolean otfvis;

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "*..1")
	private Sample sample = new Sample();

	@CommandLine.Option(names = {"--no-lanes"}, defaultValue = "false", description = "Deactivate the use of lane information")
	private boolean noLanes;

	@CommandLine.Option(names = {"--lane-capacity"}, defaultValue = "1", description = "Scale lane capacity by this factor.")
	private double laneCapacity;

	@CommandLine.Option(names = {"--free-flow"}, defaultValue = "1", description = "Scale up free flow speed of slow links.")
	private double freeFlowFactor;

	public RunDuesseldorfScenario() {
		super(String.format("scenarios/input/duesseldorf-%s-1pct.config.xml", VERSION));
	}

	public static void main(String[] args) {
		MATSimApplication.run(RunDuesseldorfScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		//addDefaultActivityParams(config);

		for (long ii = 600; ii <= 97200; ii += 600) {

			for (String act : List.of("home", "restaurant", "other", "visit", "errands", "educ_higher", "educ_secondary")) {
				config.planCalcScore().addActivityParams(new ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
			}

			config.planCalcScore().addActivityParams(new ActivityParams("work_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new ActivityParams("business_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
			config.planCalcScore().addActivityParams(new ActivityParams("shopping_" + ii + ".0").setTypicalDuration(ii).setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
		}

		// Config changes for larger samples
		if (sample.getSize() != 1) {

			String postfix = "-" + sample.getSize() + "pct";

			config.plans().setInputFile(config.plans().getInputFile().replace("-1pct",  postfix));
			config.controler().setRunId(config.controler().getRunId().replace("-1pct", postfix));
			config.controler().setOutputDirectory(config.controler().getOutputDirectory().replace("-1pct", postfix));

			config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
			config.qsim().setStorageCapFactor(sample.getSize() / 100.0);
		}

		if (noLanes) {

			config.controler().setLinkToLinkRoutingEnabled(false);
			config.network().setLaneDefinitionsFile(null);
			config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(false);

			config.controler().setRunId(config.controler().getRunId() + "-no-lanes");
			config.controler().setOutputDirectory(config.controler().getOutputDirectory() + "-no-lanes");

			config.controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);

		} else {

			if (laneCapacity != 1.0)
				config.controler().setOutputDirectory(config.controler().getOutputDirectory() + "-cap_" + laneCapacity);

		}


		if (freeFlowFactor != 1)
			config.controler().setOutputDirectory(config.controler().getOutputDirectory() + "-ff_" + freeFlowFactor);

		// config.planCalcScore().addActivityParams(new ActivityParams("freight").setTypicalDuration(12. * 3600.));
		config.planCalcScore().addActivityParams(new ActivityParams("car interaction").setTypicalDuration(60));

		config.plans().setHandlingOfPlansWithoutRoutingMode(PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		// scale lane capacities
		for (LanesToLinkAssignment l2l : scenario.getLanes().getLanesToLinkAssignments().values()) {
			for (Lane lane : l2l.getLanes().values()) {
				lane.setCapacityVehiclesPerHour(lane.getCapacityVehiclesPerHour() * laneCapacity);
			}
		}

		// scale free flow speed
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getFreespeed() < 25.5 / 3.6) {
				link.setFreespeed(link.getFreespeed() * freeFlowFactor);
			}
		}
	}

	@Override
	protected void prepareControler(Controler controler) {

		if (otfvis)
			controler.addOverridingModule(new OTFVisWithSignalsLiveModule());

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new SwissRailRaptorModule());
			}
		});
	}

	/**
	 * Option group for desired sample size.
	 */
	static final class Sample {

		@CommandLine.Option(names = {"--25pct", "--prod"}, defaultValue = "false", description = "Use the 25pct scenario")
		private boolean p25;

		@CommandLine.Option(names = {"--10pct"}, defaultValue = "false", description = "Use the 10pct sample")
		private boolean p10;

		@CommandLine.Option(names = {"--1pct"}, defaultValue = "true", description = "Use the 1pct sample")
		private boolean p1 = true;

		/**
		 * Get configured sample size.
		 */
		int getSize() {
			if (p25) return 25;
			if (p10) return 10;
			if (p1) return 1;
			throw new IllegalStateException("No sample size defined");
		}

	}
}
