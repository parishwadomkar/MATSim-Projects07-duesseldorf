package org.matsim.freight;

import org.apache.commons.lang3.mutable.MutableInt;
import picocli.CommandLine;
import org.geotools.data.FeatureReader;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * This code generates German wide long distance freight trip.
 * Input: Freight data; German major road network; NUTS3 shape file; Look up
 * table between NUTS zone ID and Verkehrszellen
 *
 * Author: Chengqi Lu
 */

@CommandLine.Command(
		name = "generate-short-distance-trips",
		description = "Add short-distance walk trips to a population",
		showDefaultValues = true
)

@Deprecated
public class GenerateGermanWideFreightTrips implements Callable<Integer> {
	@CommandLine.Parameters(arity = "0..1", paramLabel = "INPUT", description = "Path to the raw data directory")
	private Path rawDataDirectory;
	// An example of the path: "D:/svn-shared/projects/komodnext/data/freight/originalData"

	@CommandLine.Option(names = "--pct", defaultValue = "100", description = "Scaling factor of the freight" +
			" traffic (in percentage)", required = true)
	private int pct;

	@CommandLine.Option(names = "--truckLoad", defaultValue = "16.0", description = "Average load of truck",
			required = true)
	private double averageTruckLoad;

	private static final Random RND = new Random(4711);

	public static void main(String[] args) {
		System.exit(new CommandLine(new GenerateGermanWideFreightTrips()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		String shapeFilePath = rawDataDirectory + "/NUTS3/NUTS3_2010_DE.shp";
		String networkPath = rawDataDirectory + "/german-primary-road.network.xml.gz";
		String freightDataPath = rawDataDirectory + "/ketten-2010.csv";
		String lookupTablePath = rawDataDirectory + "/lookup-table.csv";

		double adjustedTrucksLoad = averageTruckLoad * (100.0 / pct) * 365; // 1 year = 365 days

		// Load config, scenario and network
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:5677");
		config.network().setInputFile(networkPath);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network network = scenario.getNetwork();
		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		// Extracting relevant zones and associate them with the all the links inside
		Map<String, List<Id<Link>>> regionLinksMap = new HashMap<>();
		List<Link> links = network.getLinks().values().stream().filter(l -> l.getAllowedModes().contains("car"))
				.collect(Collectors.toList());

		System.out.println("Reading Shape File now...");
		ShapefileDataStore ds = (ShapefileDataStore) FileDataStoreFinder.getDataStore(new File(shapeFilePath));
		ds.setCharset(StandardCharsets.UTF_8);
		FeatureReader<SimpleFeatureType, SimpleFeature> it = ds.getFeatureReader();

		Map<String, Geometry> regions = new HashMap<>();
		while (it.hasNext()) {
			SimpleFeature feature = it.next();
			Geometry region = (Geometry) feature.getDefaultGeometry();
			String nutsId = feature.getAttribute("NUTS_ID").toString();
			regions.put(nutsId, region);
		}
		it.close();
		System.out.println("Shape file loaded. There are in total " + regions.keySet().size() + " regions");

		System.out.println("Start processing the region");
		int processed = 0;
		for (String nutsId : regions.keySet()) {
			Geometry region = regions.get(nutsId);
			boolean regionIsRelevant = false;
			List<Id<Link>> linksInsideRegion = new ArrayList<>();
			for (Link link : links) {
				if (isCoordWithinGeometry(link.getToNode().getCoord(), region)) {
					regionIsRelevant = true;
					linksInsideRegion.add(link.getId());
				}
			}
			if (regionIsRelevant) {
				regionLinksMap.put(nutsId, linksInsideRegion);
			}
			processed += 1;
			if (processed % 10 == 0) {
				System.out.println("Analysis in progress: " + processed + " regions have been processed");
			}
		}

		// Reading the look up table (RegionID-RegionName-Table.csv)
		Map<String, String> lookUpTable = new HashMap<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(lookupTablePath)));
			reader.readLine(); // Skip first line
			String line = reader.readLine();
			while (line != null) {
				String nutsId = line.split(";")[3];
				String zoneId = line.split(";")[0];
				lookUpTable.put(zoneId, nutsId);
				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("Region analysis complete!");
		System.out.println("There are " + regionLinksMap.keySet().size() + " relevant regions");

		Set<String> relevantRegionNutsIds = regionLinksMap.keySet();
		Map<String, String> lookUpTableCore = new HashMap<>();
		for (String regionId : lookUpTable.keySet()) {
			if (relevantRegionNutsIds.contains(lookUpTable.get(regionId))) {
				lookUpTableCore.put(regionId, lookUpTable.get(regionId));
			}
		}
		Set<String> relevantRegionIds = lookUpTableCore.keySet();

		// Read freight data and generate freight population
		try {
			MutableInt totalGeneratedPerson = new MutableInt();
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(freightDataPath)));
			reader.readLine(); // Skip first line
			String line = reader.readLine();
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

				if (relevantRegionIds.contains(originVL) && relevantRegionIds.contains(destinationVL)
						&& modeVL.equals("2") && !tonVL.equals("0")) {
					double trucks = Double.parseDouble(tonVL) / adjustedTrucksLoad;
					int numOfTrucks = 0;
					if (trucks < 1) {
						if (RND.nextDouble() < trucks) {
							numOfTrucks = 1;
						}
					} else {
						numOfTrucks = (int) (Math.floor(trucks) + 1);
					}
					List<Id<Link>> linksInOrigin = regionLinksMap.get(lookUpTableCore.get(originVL));
					Id<Link> fromLinkId = linksInOrigin.get(RND.nextInt(linksInOrigin.size()));

					List<Id<Link>> linksInDestination = regionLinksMap.get(lookUpTableCore.get(destinationVL));
					Id<Link> toLinkId = linksInDestination.get(RND.nextInt(linksInDestination.size()));

					generateFreightPlan(network, fromLinkId, toLinkId, numOfTrucks, goodType, population,
							populationFactory, totalGeneratedPerson);
				}

				if (relevantRegionIds.contains(originHL) && relevantRegionIds.contains(destinationHL)
						&& modeHL.equals("2") && !tonHL.equals("0")) {
					double trucks = Double.parseDouble(tonHL) / adjustedTrucksLoad;
					int numOfTrucks = 0;
					if (trucks < 1) {
						if (RND.nextDouble() < trucks) {
							numOfTrucks = 1;
						}
					} else {
						numOfTrucks = (int) (Math.floor(trucks) + 1);
					}

					List<Id<Link>> linksInOrigin = regionLinksMap.get(lookUpTableCore.get(originHL));
					Id<Link> fromLinkId = linksInOrigin.get(RND.nextInt(linksInOrigin.size()));

					List<Id<Link>> linksInDestination = regionLinksMap.get(lookUpTableCore.get(destinationHL));
					Id<Link> toLinkId = linksInDestination.get(RND.nextInt(linksInDestination.size()));

					generateFreightPlan(network, fromLinkId, toLinkId, numOfTrucks, goodType, population,
							populationFactory, totalGeneratedPerson);
				}

				if (relevantRegionIds.contains(originNL) && relevantRegionIds.contains(destinationNL)
						&& modeNL.equals("2") && !tonNL.equals("0")) {
					double trucks = Double.parseDouble(tonNL) / adjustedTrucksLoad;
					int numOfTrucks = 0;
					if (trucks < 1) {
						if (RND.nextDouble() < trucks) {
							numOfTrucks = 1;
						}
					} else {
						numOfTrucks = (int) (Math.floor(trucks) + 1);
					}

					List<Id<Link>> linksInOrigin = regionLinksMap.get(lookUpTableCore.get(originNL));
					Id<Link> fromLinkId = linksInOrigin.get(RND.nextInt(linksInOrigin.size()));

					List<Id<Link>> linksInDestination = regionLinksMap.get(lookUpTableCore.get(destinationNL));
					Id<Link> toLinkId = linksInDestination.get(RND.nextInt(linksInDestination.size()));

					generateFreightPlan(network, fromLinkId, toLinkId, numOfTrucks, goodType, population,
							populationFactory, totalGeneratedPerson);
				}
				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Write population
		System.out.println("Writing population file...");
		System.out.println("There are in total " + population.getPersons().keySet().size() + " freight trips");
		PopulationWriter pw = new PopulationWriter(population);
		String outputPath = rawDataDirectory.toString() + "/../german-wide-freight-" + pct + "pct.plans.xml.gz";
		pw.write(outputPath);
		return 0;
	}


	private static boolean isCoordWithinGeometry(Coord coord, Geometry geometry) {
		Point point = MGC.coord2Point(coord);
		return point.within(geometry);
	}

	private static void generateFreightPlan(Network network, Id<Link> fromLinkId, Id<Link> toLinkId, int numOfTrucks,
											String goodType, Population population, PopulationFactory populationFactory,
											MutableInt totalGeneratedPersons) {
		if (fromLinkId.toString().equals(toLinkId.toString())) {
			return; // We don't have further information on the trips within the same region
		}

		int generated = 0;
		while (generated < numOfTrucks) {
			Person freightPerson = populationFactory.createPerson(
					Id.create("freight_" + totalGeneratedPersons.intValue(), Person.class));
			freightPerson.getAttributes().putAttribute("subpopulation", "freight");
			freightPerson.getAttributes().putAttribute("type_of_good", goodType);

			Plan plan = populationFactory.createPlan();
			Activity act0 = populationFactory.createActivityFromLinkId("freight_start", fromLinkId);
			act0.setCoord(network.getLinks().get(fromLinkId).getCoord());
			act0.setEndTime(RND.nextInt(86400));
			Leg leg = populationFactory.createLeg("freight");
			Activity act1 = populationFactory.createActivityFromLinkId("freight_end", toLinkId);
			act1.setCoord(network.getLinks().get(toLinkId).getCoord());

			plan.addActivity(act0);
			plan.addLeg(leg);
			plan.addActivity(act1);
			freightPerson.addPlan(plan);
			population.addPerson(freightPerson);

			generated += 1;
			totalGeneratedPersons.increment();
		}
	}
}
