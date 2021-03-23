package org.matsim.freight;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Triple;
import org.geotools.data.FeatureReader;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class FreightTripGenerationPartA {
	// This is the first part of the freight trip generation. The input argument is
	// the output path of the
	// Region Id and Region Name data. Important, due to the incompatibility of the
	// different data base,
	// manual adjustment to the output file of this script is necessary before
	// running the next part!!!
	// And this is the reason why the freight generation is seprated into two parts

	private static final String SHAPEFILE_PATH = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios"
			+ "/countries/de/duesseldorf/duesseldorf-v1.0/input/shapeFiles/freight/tranfomredShapeFile.shp";
//	private static final String NETWORK_FILE = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios"
//			+ "/countries/de/duesseldorf/duesseldorf-v1.0/input/duesseldorf-v1.0-network-with-pt.xml.gz";
	private static final String NETWORK_FILE = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\network_osm_primary.xml.gz";
	private static final String VERKEHRSZELLEN = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios"
			+ "/countries/de/duesseldorf/duesseldorf-v1.0/original-data/freight-raw-data/verkehrszellen.csv";

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			args = new String[] {
					"C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\RegionID-RegionName-Table2.csv" };
		}
		String outputPath = args[0];

		// Load config, scenario and network
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:25832");
		config.network().setInputFile(NETWORK_FILE);
		config.network().setInputCRS("EPSG:5677");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network network = scenario.getNetwork();

		// Read shapeFile and get geometry of each region
		System.out.println("Reading Shape File now...");
		ShapefileDataStore ds = (ShapefileDataStore) FileDataStoreFinder.getDataStore(new URL(SHAPEFILE_PATH));
		ds.setCharset(StandardCharsets.UTF_8);
		FeatureReader<SimpleFeatureType, SimpleFeature> it = ds.getFeatureReader();

		// Map<String, Geometry> regions = new HashMap<>();
		List<Triple<String, String, Geometry>> regions = new ArrayList<>();
		while (it.hasNext()) {
			SimpleFeature feature = it.next();
			Geometry region = (Geometry) feature.getDefaultGeometry();
			String regionName = feature.getAttribute("NUTS_NAME").toString();
			String nutsId = feature.getAttribute("NUTS_ID").toString();
			Triple<String, String, Geometry> regionEntry = Triple.of(regionName, nutsId, region);
			regions.add(regionEntry);
		}
		it.close();
		System.out.println("Shape File successfully loaded. There are in total " + regions.size() + " regions");

		// Extract the relevant regions
		int counter = 0;
		System.out.println("Starting extracting relevant regions");
		List<Triple<String, String, Geometry>> relevantRegions = new ArrayList<>();
		List<Link> links = network.getLinks().values().stream().filter(l -> l.getAllowedModes().contains("car"))
				.collect(Collectors.toList());
		for (Triple<String, String, Geometry> regionEntry : regions) {
			Geometry region = regionEntry.getRight();
			for (Link link : links) {
				if (isCoordWithinGeometry(link.getToNode().getCoord(), region)) {
					relevantRegions.add(regionEntry);
					break;
				}
			}
			counter += 1;
			if (counter % 10 == 0) {
				System.out.println("Analysis in progress: " + counter + " regions have been processed");
			}
		}
		int numOfRelevantRegions = relevantRegions.size();
		System.out.println(
				"Analysis complete! There are in total " + numOfRelevantRegions + " regions within the network range");

		// Look up the region ID for each relevant regions from the "VERKEHRSZELLEN"
		Map<String, String> verkehrszellen = new HashMap<>();
		String[][] outputdata = new String[numOfRelevantRegions][3];
		try (InputStream is = new URL(VERKEHRSZELLEN).openStream();
				InputStreamReader isr = new InputStreamReader(is, StandardCharsets.ISO_8859_1);
				BufferedReader reader = new BufferedReader(isr)) {

			reader.readLine(); // Skip the first line
			String str;
			while ((str = reader.readLine()) != null) {
				String regionIdAndName = str.replace(", ", " ");
				String regionName = regionIdAndName.split(";")[1];
				String regionId = regionIdAndName.split(";")[0];
				verkehrszellen.put(regionName.split(" ")[0], regionId);
				System.out.println("Region " + regionName + " has the ID of " + regionId);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		for (int i = 0; i < relevantRegions.size(); i++) {
			Triple<String, String, Geometry> regionEntry = relevantRegions.get(i);
			String regionName = regionEntry.getLeft();
			String modifiedRegionName = regionName.split(",")[0].split(" ")[0];
			outputdata[i][0] = modifiedRegionName;
			outputdata[i][1] = regionEntry.getMiddle();
			String regionId = verkehrszellen.get(modifiedRegionName);
			outputdata[i][2] = regionId;
			System.out.println(
					regionEntry.getLeft() + ": NUTS ID = " + regionEntry.getMiddle() + "; Vekehrszelle = " + regionId);
		}

		// Write data into csv file
		System.out.println("Writing CSV File now");
		FileWriter csvWriter = new FileWriter(outputPath);
		csvWriter.append("Region Name");
		csvWriter.append(",");
		csvWriter.append("NUTS Id");
		csvWriter.append(",");
		csvWriter.append("Verkehrszelle");
		csvWriter.append("\n");

		for (int i = 0; i < outputdata.length; i++) {
			csvWriter.append(outputdata[i][0]);
			csvWriter.append(",");
			csvWriter.append(outputdata[i][1]);
			csvWriter.append(",");
			csvWriter.append(outputdata[i][2]);
			csvWriter.append("\n");
		}

		csvWriter.flush();
		csvWriter.close();
	}

	private static boolean isCoordWithinGeometry(Coord coord, Geometry geometry) {
		Point point = MGC.coord2Point(coord);
		if (point.within(geometry)) {
			return true;
		}
		return false;
	}

}
