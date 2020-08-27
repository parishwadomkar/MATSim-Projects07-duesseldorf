package org.matsim.prepare;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetCountData {

	private static final Logger logger = Logger.getLogger(CreateCounts.class);

	Map<String, CountingData> countData(String filePath1, String filePath2, Map<String, NodeMatcher.MatchedLinkID> nodeMatcher) throws IOException {

		var result1 = readData(filePath1, nodeMatcher);
		var result2 = readData(filePath2, nodeMatcher);

		for (var entry : result1.entrySet()) {
			result2.put(entry.getKey(), entry.getValue());
		}

		logger.info("###############################################");
		logger.info("#\t\t\t\t\t\t\t\t\t\t\t\t#");
		logger.info("#\t\t\t All Counts were imported! \t\t\t#");
		logger.info("#\t\t\t  " + result2.keySet().size() + " stations were found!\t\t\t#");
		logger.info("#\t\t\t\t\t\t\t\t\t\t\t\t#");
		logger.info("###############################################");
		return result2;
	}

	private Map<String, CountingData> readData(String filePath, Map<String, NodeMatcher.MatchedLinkID> nodeMatcher) throws IOException {

		Map<String, CountingData> data = new HashMap<>();

		try (var reader = new FileReader(filePath)) {
			try (var parser = CSVFormat.newFormat(';').withAllowMissingColumnNames().withFirstRecordAsHeader().parse(reader)) {

				for (var record : parser) {

					var idR1 = record.get("Zst") + "_R1";
					var idR2 = record.get("Zst") + "_R2";
					if (containsNode(nodeMatcher, idR1, idR2) && isIntresstingWeekday(record)) {

						if (isValid(record, "PLZ_R1")) {

							var linkId1 = nodeMatcher.get(idR1).getLinkID();
							var countData1 = data.computeIfAbsent(idR1, key -> new CountingData(key, linkId1));

							var hour = record.get("Stunde");
							var value1 = Integer.parseInt(record.get("PLZ_R1").trim());

							countData1.addValue(hour, value1);

						}

						if (isValid(record, "PLZ_R2")) {

							var linkId2 = nodeMatcher.get(idR2).getLinkID();
							var countData2 = data.computeIfAbsent(idR2, key -> new CountingData(key, linkId2));

							var hour = record.get("Stunde");
							var value2 = Integer.parseInt(record.get("PLZ_R2").trim());

							countData2.addValue(hour, value2);

						}
					}
				}
			}
		}

		for (Map.Entry<String, CountingData> value : data.entrySet()) {

			for (Map.Entry<String, List<Integer>> count : value.getValue().values.entrySet()) {

				value.getValue().result.put(count.getKey(), value.getValue().averageForHour(count.getKey()));

			}
		}

		return data;

	}

	private boolean isIntresstingWeekday(CSVRecord record) {
		return record.get("Wotag").trim().equals("2") || record.get("Wotag").trim().equals("3") || record.get("Wotag").trim().equals("4");
	}

	private boolean containsNode(Map<String, NodeMatcher.MatchedLinkID> nodeMatcher, String idR1, String idR2) {
		return nodeMatcher.containsKey(idR1) && nodeMatcher.containsKey(idR2);
	}

	private boolean isValid(CSVRecord record, String plz_r2) {
		return !record.get(plz_r2).trim().equals("-1") && !record.get(plz_r2).trim().equals("0");
	}

	@Getter
	@RequiredArgsConstructor
	@ToString
	static class CountingData {

		private final String stationId;
		private final String linkId;
		private final Map<String, List<Integer>> values = new HashMap<>();
		private final Map<String, Integer> result = new HashMap<>();

		void addValue(String hour, int value) {
			values.computeIfAbsent(hour, k -> new ArrayList<>()).add(value);
		}

		public int averageForHour(String hour) {

			int sum = 0;

			for (var value : values.get(hour)) {
				sum += value;
			}

			return sum / values.get(hour).size();
		}
	}
}