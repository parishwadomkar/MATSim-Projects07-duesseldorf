package org.matsim.freight;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

public class GenerateFreightPlans {
	private static final String NETWORK_FILE = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\Scenario\\duesseldorf-v1.0-network-with-pt.xml.gz";
	private static final String SHAPEFILE_PATH = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\ShapeFile\\tranfomredShapeFile.shp";
	private static final String REGION_LINK_OUTPUT_PATH = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\region-link-map.csv";

	public static void main(String[] args) throws IOException {
		// Load config, scenario and network
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:25832");
		config.network().setInputFile(NETWORK_FILE);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network network = scenario.getNetwork();

		// Read shapeFile and get geometry of each region
		System.out.println("Reading Shape File now...");
		ShapefileDataStore ds = (ShapefileDataStore) FileDataStoreFinder.getDataStore(new File(SHAPEFILE_PATH));
		ds.setCharset(StandardCharsets.UTF_8);
		FeatureReader<SimpleFeatureType, SimpleFeature> it = ds.getFeatureReader();

		Map<String, Geometry> regions = new HashMap<>();
		while (it.hasNext()) {
			SimpleFeature feature = it.next();
			Geometry region = (Geometry) feature.getDefaultGeometry();
			String regionName = feature.getAttribute("NUTS_NAME").toString();
			regions.put(regionName, region);
		}
		it.close();
		System.out
				.println("Shape File successfully loaded. There are in total " + regions.keySet().size() + " regions");

		// Get one link in each region
		int counter = 0;
		System.out.println("Starting searching for link in each region");
		Map<String, Link> regionLinkMap = new HashMap<>();
		List<Link> links = network.getLinks().values().stream().filter(l -> l.getAllowedModes().contains("car"))
				.collect(Collectors.toList());
		Collections.shuffle(links);
		for (String regionName : regions.keySet()) {
			Geometry region = regions.get(regionName);
			for (Link link : links) {
				if (isCoordWithinGeometry(link.getToNode().getCoord(), region)) {
					regionLinkMap.put(regionName, link);
					break;
				}
			}
			counter += 1;
			if (counter % 10 == 0) {
				System.out.println("Analysis in progress: " + counter + " regions have been processed");
			}
		}
		System.out.println("Analysis complete! There are in total " + regionLinkMap.keySet().size()
				+ " regions within the network range");

		// Write region - link map into a CSV file
		System.out.println("Writing CSV File now");
		FileWriter csvWriter = new FileWriter(REGION_LINK_OUTPUT_PATH);
		csvWriter.append("Region Name");
		csvWriter.append(",");
		csvWriter.append("Link Id");
		csvWriter.append("\n");

		for (String regionName : regionLinkMap.keySet()) {
			String modifiedName1 = regionName.replace(", ", " ");
			String modifiedName = modifiedName1.replace(",", " ");
			csvWriter.append(modifiedName);
			csvWriter.append(",");
			csvWriter.append(regionLinkMap.get(regionName).getId().toString());
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
