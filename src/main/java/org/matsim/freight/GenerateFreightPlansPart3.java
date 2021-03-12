package org.matsim.freight;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class GenerateFreightPlansPart3 {
	private static final String NETWORK_FILE = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\"
			+ "Scenario\\duesseldorf-v1.0-network-with-pt.xml.gz";
	private static final String REGION_LINK_DATA = "C:\\Users\\cluac\\MATSimScenarios\\"
			+ "Dusseldorf\\freight\\regionId-link-map.csv";
	private static final String FREIGHT_DATA = "C:\\Users\\cluac\\MATSimScenarios\\"
			+ "Dusseldorf\\freight\\ketten-2010.csv";
	private static final String OUTPUT_PLAN_FILE = "C:\\Users\\cluac\\MATSimScenarios\\"
			+ "Dusseldorf\\freight\\freight-only-plans.xml";
	private static final double AVERAGE_CAPACITY_OF_TRUCK = 10000;  //TODO change this 
	private static final Random RND = new Random(1234);

	public static void main(String[] args) throws IOException {
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:25832");
		config.network().setInputFile(NETWORK_FILE);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		// Read region ID - link data
		Map<String, Id<Link>> regionLinkIdMap = new HashMap<>();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(REGION_LINK_DATA));
			reader.readLine(); // Skip first line
			String line = reader.readLine();
			while (line != null) {
				String regionId = line.split(",")[0];
				String linkId = line.split(",")[1];
				regionLinkIdMap.put(regionId, Id.create(linkId, Link.class));
//				System.out.println("For region " + regionId + " we will choose link: " + linkId);
				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Read freight data
		Set<String> regionIds = regionLinkIdMap.keySet();
		int counter = 0;
		try {
			BufferedReader reader2 = new BufferedReader(new FileReader(FREIGHT_DATA));
			reader2.readLine(); // Skip first line
			String line = reader2.readLine();
			while (line != null) {
				String[] entries = line.split(";");
				String origin = entries[2];
				String destination = entries[3];
				String mode = entries[7];
				String goodType = entries[10];
				String ton = entries[16];

				// if this data entry is relevant, then create freight person(s)
				if (regionIds.contains(origin) && regionIds.contains(destination) && mode.equals("2")) {
					int numOfTrucks = (int) (Math.floor(Double.parseDouble(ton) / AVERAGE_CAPACITY_OF_TRUCK) + 1);
					int generated = 0;
					while (generated < numOfTrucks) {
						Person freightPerson = populationFactory
								.createPerson(Id.create("freight_" + Integer.toString(counter), Person.class));
						freightPerson.getAttributes().putAttribute("typeOfGood", goodType);

						Plan plan = populationFactory.createPlan();
						Activity act0 = populationFactory.createActivityFromLinkId("dummy",
								regionLinkIdMap.get(origin));
						act0.setEndTime(RND.nextInt(86400));
						Leg leg = populationFactory.createLeg("freight");
						Activity act1 = populationFactory.createActivityFromLinkId("dummy",
								regionLinkIdMap.get(destination));

						plan.addActivity(act0);
						plan.addLeg(leg);
						plan.addActivity(act1);
						freightPerson.addPlan(plan);
						population.addPerson(freightPerson);

						generated += 1;
						counter += 1;
					}
				}
				line = reader2.readLine();
			}
			reader2.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Write population
		System.out.println("Writing population file...");
		System.out.println("There are in total " + population.getPersons().keySet().size() + " freight trips");
		PopulationWriter pw = new PopulationWriter(population);
		pw.write(OUTPUT_PLAN_FILE);
	}

}
