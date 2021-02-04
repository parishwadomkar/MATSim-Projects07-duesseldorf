package org.matsim.run;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

public class RunDusseldorfTesting {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public final void runNoLaneTest() {
		try {
			final String[] args = { "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/input/test.config.xml" };

			Config config = ConfigUtils.loadConfig(args[0]);
			config.controler().setLastIteration(1);
			config.strategy().setFractionOfIterationsToDisableInnovation(1);
			config.controler()
					.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
			config.controler().setOutputDirectory(utils.getOutputDirectory());
			addDefaultActivityParams(config);
			
			// No lane setting Start
			config.qsim().setUseLanes(false);
			config.controler().setLinkToLinkRoutingEnabled(false);
			config.network().setLaneDefinitionsFile(null);
			config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(false);
			config.controler().setRunId(config.controler().getRunId() + "-no-lanes");
			config.controler().setOutputDirectory(config.controler().getOutputDirectory() + "-no-lanes");
			config.controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks);
			// End
			
			Scenario scenario = ScenarioUtils.loadScenario(config);
			Controler controler = new Controler(scenario);
			controler.run();

		} catch (Exception ee) {
			throw new RuntimeException(ee);
		}
	}

	
	@Test
	public final void runWithLaneTest() {
		try {
			final String[] args = { "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/input/test.config.xml" }; // TODO config file path

			Config config = ConfigUtils.loadConfig(args[0]);
			config.controler().setLastIteration(1);
			config.strategy().setFractionOfIterationsToDisableInnovation(1);
			config.controler()
					.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
			config.controler().setOutputDirectory(utils.getOutputDirectory());
			addDefaultActivityParams(config);

			Scenario scenario = ScenarioUtils.loadScenario(config);
			Controler controler = new Controler(scenario);
			controler.run();

		} catch (Exception ee) {
			throw new RuntimeException(ee);
		}
	}
	
	private static void addDefaultActivityParams(Config config) {
		for (long ii = 600; ii <= 97200; ii += 600) {
			config.planCalcScore().addActivityParams(
					new PlanCalcScoreConfigGroup.ActivityParams("home_" + ii + ".0").setTypicalDuration(ii));
			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii + ".0")
					.setTypicalDuration(ii).setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii + ".0")
					.setTypicalDuration(ii).setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
			config.planCalcScore()
					.addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shopping_" + ii + ".0")
							.setTypicalDuration(ii).setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(
					new PlanCalcScoreConfigGroup.ActivityParams("other_" + ii + ".0").setTypicalDuration(ii));
		}
		config.planCalcScore().addActivityParams(
				new PlanCalcScoreConfigGroup.ActivityParams("freight").setTypicalDuration(12. * 3600.));
		config.planCalcScore().addActivityParams(
				new PlanCalcScoreConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
	}
}
