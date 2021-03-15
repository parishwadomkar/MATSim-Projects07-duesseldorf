package org.matsim.freight;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableInt;
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
			+ "Dusseldorf\\freight\\10pct-freight-only-plans.xml";
	private static final double AVERAGE_CAPACITY_OF_TRUCK = 16 * 365 * 10; // average load = 16 ton, 1 year = 365 days,
																			// 10pct scenario
	private static final Random RND = new Random(1234);

	public static void main(String[] args) throws IOException {
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:25832");
		config.network().setInputFile(NETWORK_FILE);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network network = scenario.getNetwork();
		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		// Read region ID - link data
		Map<String, Id<Link>> regionLinkIdMap = new HashMap<>();
		Map<String, String> regionNameMap = new HashMap<>();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(REGION_LINK_DATA));
			reader.readLine(); // Skip first line
			String line = reader.readLine();
			while (line != null) {
				String regionId = line.split(",")[0];
				String linkId = line.split(",")[1];
				String regionName = line.split(",")[2];
				regionLinkIdMap.put(regionId, Id.create(linkId, Link.class));
				regionNameMap.put(regionId, regionName);
				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Read freight data
		Set<String> regionIds = regionLinkIdMap.keySet();
		MutableInt counter = new MutableInt();
		try {
			BufferedReader reader2 = new BufferedReader(new FileReader(FREIGHT_DATA));
			reader2.readLine(); // Skip first line
			String line = reader2.readLine();
			while (line != null) {
				String[] dataEntry = line.split(";");
				String goodType = dataEntry[10];

				// Vorlauf
				String modeVL = dataEntry[6];
				String originVL = dataEntry[0];
				String destinationVL = dataEntry[2];
				String tonVL = dataEntry[15];

				// Hauptlauf
				String modeHL = dataEntry[7];
				String originHL = dataEntry[2];
				String destinationHL = dataEntry[3];
				String tonHL = dataEntry[16];

				// Nachlauf
				String modeNL = dataEntry[8];
				String originNL = dataEntry[3];
				String destinationNL = dataEntry[1];
				String tonNL = dataEntry[17];

				if (regionIds.contains(originVL) && regionIds.contains(destinationVL) && modeVL.equals("2")
						&& !tonVL.equals("0")) {
					int numOfTrucks = (int) (Math.floor(Double.parseDouble(tonVL) / AVERAGE_CAPACITY_OF_TRUCK) + 1);
					Id<Link> fromLinkId = regionLinkIdMap.get(originVL);
					Id<Link> toLinkId = regionLinkIdMap.get(destinationVL);
					generateFreightPlan(network, fromLinkId, toLinkId, numOfTrucks, goodType, population, populationFactory,
							counter);
				}

				if (regionIds.contains(originHL) && regionIds.contains(destinationHL) && modeHL.equals("2")
						&& !tonHL.equals("0")) {
					int numOfTrucks = (int) (Math.floor(Double.parseDouble(tonHL) / AVERAGE_CAPACITY_OF_TRUCK) + 1);
					Id<Link> fromLinkId = regionLinkIdMap.get(originHL);
					Id<Link> toLinkId = regionLinkIdMap.get(destinationHL);
					generateFreightPlan(network, fromLinkId, toLinkId, numOfTrucks, goodType, population, populationFactory,
							counter);
				}

				if (regionIds.contains(originNL) && regionIds.contains(destinationNL) && modeNL.equals("2")
						&& !tonNL.equals("0")) {
					int numOfTrucks = (int) (Math.floor(Double.parseDouble(tonNL) / AVERAGE_CAPACITY_OF_TRUCK) + 1);
					Id<Link> fromLinkId = regionLinkIdMap.get(originNL);
					Id<Link> toLinkId = regionLinkIdMap.get(destinationNL);
					generateFreightPlan(network, fromLinkId, toLinkId, numOfTrucks, goodType, population, populationFactory,
							counter);
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

	private static void generateFreightPlan(Network network, Id<Link> fromLinkId, Id<Link> toLinkId, int numOfTrucks, String goodType,
			Population population, PopulationFactory populationFactory, MutableInt counter) {
		if (fromLinkId.toString().equals(toLinkId.toString())) {
			return; // We don't have further information on the trips within the same region
		}

		int generated = 0;
		while (generated < numOfTrucks) {
			Person freightPerson = populationFactory
					.createPerson(Id.create("freight_" + Integer.toString(counter.intValue()), Person.class));
			freightPerson.getAttributes().putAttribute("subpopulation", "freight");
			freightPerson.getAttributes().putAttribute("type_of_good", goodType);

			Plan plan = populationFactory.createPlan();
			Activity act0 = populationFactory.createActivityFromLinkId("other_600.0", fromLinkId);
//			if (network.getLinks().get(fromLinkId)==null){
//				System.out.println("this link has some problem: " + fromLinkId.toString());//TODO delete after testing
//			}
//			if (network.getLinks().get(toLinkId)==null){
//				System.out.println("this link has some problem: " + toLinkId.toString());//TODO  delete after testing
//			}
			act0.setCoord(network.getLinks().get(fromLinkId).getCoord());
			act0.setEndTime(RND.nextInt(86400));
			Leg leg = populationFactory.createLeg("freight");
			Activity act1 = populationFactory.createActivityFromLinkId("other_600.0", toLinkId);
			act1.setCoord(network.getLinks().get(toLinkId).getCoord());

			plan.addActivity(act0);
			plan.addLeg(leg);
			plan.addActivity(act1);
			freightPerson.addPlan(plan);
			population.addPerson(freightPerson);

			generated += 1;
			counter.increment();
		}

	}

}
