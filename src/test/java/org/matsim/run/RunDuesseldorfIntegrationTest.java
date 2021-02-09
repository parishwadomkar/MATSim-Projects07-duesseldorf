package org.matsim.run;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.testcases.MatsimTestUtils;

public class RunDuesseldorfIntegrationTest {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public final void runOneAgentTest() {

		Config config = ConfigUtils.loadConfig("scenarios/input/test.config.xml");
		config.plans().setInputFile("new-test-plans.xml");
		config.controler().setLastIteration(1);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		addDefaultActivityParams(config);

		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] { "--no-lanes" });

	}

	@Test
	public final void runNoLaneTest() {

		Config config = ConfigUtils.loadConfig("scenarios/input/test.config.xml");
		config.controler().setLastIteration(1);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		addDefaultActivityParams(config);

		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] { "--no-lanes" });
	}

	@Test
	public final void runWithLaneTest() {
		Config config = ConfigUtils.loadConfig("scenarios/input/test.config.xml");
		config.controler().setLastIteration(1);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		addDefaultActivityParams(config);

		// default options
		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] {});

	}

	/**
	 * Adds default activity parameter to the plan score calculation.
	 */
	private void addDefaultActivityParams(Config config) {
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
	}

}
