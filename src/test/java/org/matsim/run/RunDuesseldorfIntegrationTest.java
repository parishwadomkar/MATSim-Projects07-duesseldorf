package org.matsim.run;

import java.util.Map;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.testcases.MatsimTestUtils;

public class RunDuesseldorfIntegrationTest {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public final void runToyExamplePopulationTest() {
		Config config = ConfigUtils.loadConfig("scenarios/input/duesseldorf-v1.0-1pct.config.xml");
		config.plans().setInputFile("test/input/v2.0-testing.plan.xml");
		config.controler().setLastIteration(1);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());

		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] { "--no-lanes", "--infiniteCapacity" });
	}


	@Test
	public final void runNoLaneTestNormalCapacity() {
		Config config = ConfigUtils.loadConfig("scenarios/input/duesseldorf-v1.0-1pct.config.xml");
		config.controler().setLastIteration(0);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] { "--no-lanes" });
	}

	@Test
	public final void runNoLaneTestIncreasedCapacity() {
		Config config = ConfigUtils.loadConfig("scenarios/input/duesseldorf-v1.0-1pct.config.xml");
		config.controler().setLastIteration(0);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());

		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] { "--no-lanes", "--infiniteCapacity" });
	}

	@Test
	public final void runWithLaneTest() {
		Config config = ConfigUtils.loadConfig("scenarios/input/duesseldorf-v1.0-1pct.config.xml");
		config.controler().setLastIteration(1);
		config.strategy().setFractionOfIterationsToDisableInnovation(1);
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		
		org.matsim.core.controler.Controler controler = MATSimApplication.prepare(RunDuesseldorfScenario.class, config, new String[] {});
		downsample(controler.getScenario().getPopulation().getPersons(), 0.01);
		
		controler.run();
		
		// default options
//		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] {});

	}

	private static void downsample( final Map<Id<Person>, ? extends Person> map, final double sample) {
		final Random rnd = MatsimRandom.getLocalInstance();
		map.values().removeIf( person -> rnd.nextDouble() > sample ) ;
	}

	// Speical test (for debugging only)
//	@Test
//	public void singleLink() {
//		Config config = ConfigUtils.loadConfig("scenarios/input/duesseldorf-v1.0-1pct.config.xml");
//		config.plans().setInputFile("test/input/test-single-link-plans.xml");
//		// run for only 20 minutes
//		config.qsim().setEndTime(20 * 60);
//		config.controler().setLastIteration(0);
//		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
//		config.controler().setOutputDirectory(utils.getOutputDirectory());
//
//		MATSimApplication.call(RunDuesseldorfScenario.class, config, new String[] {
//				"--no-lanes"
//		});
//



}
