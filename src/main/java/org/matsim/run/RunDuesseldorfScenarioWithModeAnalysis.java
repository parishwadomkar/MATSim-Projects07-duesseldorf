package org.matsim.run;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.matsim.analysis.ModeAnalyzer;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.qnetsimengine.ConfigurableQNetworkFactory;
import org.matsim.core.mobsim.qsim.qnetsimengine.QLanesNetworkFactory;
import org.matsim.core.mobsim.qsim.qnetsimengine.QNetworkFactory;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;

import com.google.inject.Provides;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

public class RunDuesseldorfScenarioWithModeAnalysis {
	private static boolean noLanes;
	private static boolean infiniteCapacity;
	private static double capacityFactorForTrafficLight = 1;
	private static boolean noCapacityReduction; // for taking turn
	private static double freeFlowFactor = 1;
	private static boolean noModeChoice;

	public static void main(String[] args) throws IOException {
		// Run simulation
		assert args.length >= 1 : "Please enter the config file in the first entry of argumnets";
		String configPath = args[0];
		if (args.length >= 2 && args[1].equals("true")) {
			noLanes = true;
		}
		if (args.length >= 3 && args[2].equals("true")) {
			infiniteCapacity = true;
		}
		Config config = ConfigUtils.loadConfig(configPath);
		prepareConfig(config);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		prepareScenario(scenario);
		Controler controler = new Controler(scenario);
		prepareControler(controler);

		controler.run();

		// Run Mode analysis
		Path outputDirectorypath = Paths.get(config.controler().getOutputDirectory());
		ModeAnalyzer modeAnalyzer = new ModeAnalyzer(outputDirectorypath, "*");
		modeAnalyzer.run();
	}

	private static void prepareConfig(Config config) {

		// addDefaultActivityParams(config);

		for (long ii = 600; ii <= 97200; ii += 600) {

			for (String act : List.of("home", "restaurant", "other", "visit", "errands", "educ_higher",
					"educ_secondary")) {
				config.planCalcScore()
						.addActivityParams(new ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
			}

			config.planCalcScore().addActivityParams(new ActivityParams("work_" + ii + ".0").setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new ActivityParams("business_" + ii + ".0").setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii)
					.setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
			config.planCalcScore().addActivityParams(new ActivityParams("shopping_" + ii + ".0").setTypicalDuration(ii)
					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
		}

		if (noLanes) {
			config.qsim().setUseLanes(false);
			config.controler().setLinkToLinkRoutingEnabled(false);
			config.network().setLaneDefinitionsFile(null);
			config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(false);
			addRunOption(config, "no-lanes");
			config.controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);
		}

		if (capacityFactorForTrafficLight != 1.0)
			addRunOption(config, "cap", capacityFactorForTrafficLight);

		if (freeFlowFactor != 1)
			addRunOption(config, "ff", freeFlowFactor);

		if (noModeChoice) {
			List<StrategyConfigGroup.StrategySettings> strategies = config.strategy().getStrategySettings().stream()
					.filter(s -> !s.getStrategyName().equals("SubtourModeChoice")).collect(Collectors.toList());

			config.strategy().clearStrategySettings();
			strategies.forEach(s -> config.strategy().addStrategySettings(s));

			addRunOption(config, "noMc");
		}

		if (noCapacityReduction)
			addRunOption(config, "no-cap-red");

		// config.planCalcScore().addActivityParams(new
		// ActivityParams("freight").setTypicalDuration(12. * 3600.));
		config.planCalcScore().addActivityParams(new ActivityParams("car interaction").setTypicalDuration(60));
		config.planCalcScore().addActivityParams(new ActivityParams("other").setTypicalDuration(600 * 3));

		// vsp defaults
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info);
		config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
//		config.qsim().setTrafficDynamics(QSimConfigGroup.TrafficDynamics.kinematicWaves);

		config.plans().setHandlingOfPlansWithoutRoutingMode(
				PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier);

		if (infiniteCapacity) {
			config.qsim().setFlowCapFactor(100000);
			config.qsim().setStorageCapFactor(100000);
			addRunOption(config, "infiniteCapacity");
		}
	}

	private static void prepareScenario(Scenario scenario) {
		if (!noLanes) {
			// scale lane capacities
			for (LanesToLinkAssignment l2l : scenario.getLanes().getLanesToLinkAssignments().values()) {
				for (Lane lane : l2l.getLanes().values()) {
					lane.setCapacityVehiclesPerHour(lane.getCapacityVehiclesPerHour() * capacityFactorForTrafficLight);
				}
			}
		}
		// scale free flow speed
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getFreespeed() < 25.5 / 3.6) {
				link.setFreespeed(link.getFreespeed() * freeFlowFactor);
			}

			// might be null, so avoid unboxing
			if (link.getAttributes().getAttribute("junction") == Boolean.TRUE
					|| "traffic_light".equals(link.getToNode().getAttributes().getAttribute("type")))
				link.setCapacity(link.getCapacity() * capacityFactorForTrafficLight);
		}

	}

	private static void prepareControler(Controler controler) {

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new SwissRailRaptorModule());
			}
		});

		controler.addOverridingQSimModule(new AbstractQSimModule() {
			@Override
			protected void configureQSim() {
			}

			@Provides
			QNetworkFactory provideQNetworkFactory(EventsManager eventsManager, Scenario scenario) {
				ConfigurableQNetworkFactory factory = new ConfigurableQNetworkFactory(eventsManager, scenario);

				TurnDependentFlowEfficiencyCalculator fe = null;
				if (!noCapacityReduction) {
					fe = new TurnDependentFlowEfficiencyCalculator(scenario);
					factory.setFlowEfficiencyCalculator(fe);
				}

				if (noLanes)
					return factory;
				else {
					QLanesNetworkFactory wrapper = new QLanesNetworkFactory(eventsManager, scenario);
					wrapper.setDelegate(factory);
					if (fe != null)
						wrapper.setFlowEfficiencyCalculator(fe);

					return wrapper;
				}
			}
		});

	}

	/**
	 * Add and an option and value to run id and output folder.
	 */
	private static void addRunOption(Config config, String option, Object value) {

		String postfix = "-" + option + "_" + value;

		String outputDir = config.controler().getOutputDirectory();
		if (outputDir.endsWith("/")) {
			config.controler().setOutputDirectory(outputDir.substring(0, outputDir.length() - 1) + postfix + "/");
		} else
			config.controler().setOutputDirectory(outputDir + postfix);

		config.controler().setRunId(config.controler().getRunId() + postfix);
	}

	/**
	 * Add and an option to run id and output folder, delimited by "_".
	 */
	private static void addRunOption(Config config, String option) {
		addRunOption(config, option, "");
	}
}
