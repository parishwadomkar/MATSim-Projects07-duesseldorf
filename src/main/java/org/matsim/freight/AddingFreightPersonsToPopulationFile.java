package org.matsim.freight;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "add-freight-trips-tp-population",
		description = "Add freight trips to original population",
		showDefaultValues = true
)

public class AddingFreightPersonsToPopulationFile implements Callable<Integer> {
	// In this part, the freight only plan will be merged with the population plan
	// Coordinate System transformation will be performed in this step.
	@CommandLine.Option(names = "--input", description = "Path to the original population", required = true)
	private Path inputPopulation;

	@CommandLine.Option(names = "--freight", description = "Path to the freight only plans", required = true)
	private Path freightOnlyPlans;

	@CommandLine.Option(names = "--output", description = "Output path", required = true)
	private Path outputPath;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AddingFreightPersonsToPopulationFile()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:25832");
		config.plans().setInputFile(inputPopulation.toString());
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Population population = scenario.getPopulation();

		Config freightConfig = ConfigUtils.createConfig();
		freightConfig.global().setCoordinateSystem("EPSG:25832");
		freightConfig.plans().setInputFile(freightOnlyPlans.toString());
		freightConfig.plans().setInputCRS("EPSG:5677");
		Scenario freightScenario = ScenarioUtils.loadScenario(freightConfig);
		Population freightOnlyPlans = freightScenario.getPopulation();

		for (Person person : freightOnlyPlans.getPersons().values()) {
			population.addPerson(person);
		}

		// Write new population file
		// Write population
		System.out.println("Writing population file...");
		PopulationWriter pw = new PopulationWriter(population);
		pw.write(outputPath.toString());

		return 0;
	}
}
