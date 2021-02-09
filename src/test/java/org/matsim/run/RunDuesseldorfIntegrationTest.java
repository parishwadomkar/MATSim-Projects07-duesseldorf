package org.matsim.run;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
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

		// default options
		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] {});

	}

	@Test
	public void singleLink() {

		Config config = ConfigUtils.loadConfig("scenarios/input/test.config.xml");
		config.plans().setInputFile("test-single-link-plans.xml");
		// run for only 20 minutes
		config.qsim().setEndTime(20 * 60);
		config.controler().setLastIteration(0);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());

		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] {
				"--no-lanes"
		});

	}
}
