package org.matsim.freight;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GenerateFreightPlansPart2 {
	private static final String REGION_LINK_MAP_FILE = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\region-link-map.csv";
	private static final String VERKEHRSZELLEN = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\verkehrszellen.csv";
	private static final String REGION_ID_LINK_OUTPUT_PATH = "C:\\Users\\cluac\\MATSimScenarios\\Dusseldorf\\freight\\regionId-link-map.csv";

	public static void main(String[] args) throws IOException {
		Map<String, String> RegionNameAndRegionIdMap = new HashMap<>();
		Map<String, String> regionNameAndLinkMap = new HashMap<>();

		try (FileInputStream fis = new FileInputStream(VERKEHRSZELLEN);
				InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.ISO_8859_1);
				BufferedReader reader = new BufferedReader(isr)) {

			reader.readLine(); // Skip the first line
			String str;
			while ((str = reader.readLine()) != null) {
				String regionIdAndName = str.replace(", ", " ");
				String regionName = regionIdAndName.split(";")[1];
				String regionId = regionIdAndName.split(";")[0];
				RegionNameAndRegionIdMap.put(regionName.split(" ")[0], regionId);
				System.out.println("Region " + regionName + " has the ID of " + regionId);

			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		try (FileInputStream fis = new FileInputStream(REGION_LINK_MAP_FILE);
				InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
				BufferedReader reader = new BufferedReader(isr)) {

			reader.readLine(); // Skip the first line
			String str;
			while ((str = reader.readLine()) != null) {
				String regionName = str.split(",")[0];
				String linkId = str.split(",")[1];
				regionNameAndLinkMap.put(regionName.split(" ")[0], linkId);
				System.out.println("For " + regionName + ", we will choose link: " + linkId);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		// Write region ID - region name - link data into csv file
		System.out.println("Writing CSV File now");
		FileWriter csvWriter = new FileWriter(REGION_ID_LINK_OUTPUT_PATH);
		csvWriter.append("Region Id");
		csvWriter.append(",");
		csvWriter.append("Link Id");
		csvWriter.append(",");
		csvWriter.append("Region Name");
		csvWriter.append("\n");

		for (String regionName : regionNameAndLinkMap.keySet()) {
			String RegionId = RegionNameAndRegionIdMap.get(regionName);
			String LinkId = regionNameAndLinkMap.get(regionName);
			csvWriter.append(RegionId);
			csvWriter.append(",");
			csvWriter.append(LinkId);
			csvWriter.append(",");
			csvWriter.append(regionName);
			csvWriter.append("\n");
		}

		csvWriter.flush();
		csvWriter.close();
	}
}
