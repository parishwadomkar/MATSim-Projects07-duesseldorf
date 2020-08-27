package org.matsim.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CreateCounts {

	private static final Logger logger = Logger.getLogger(CreateCounts.class);

	private static final Path longTermCountsRootFederalRoad = Paths.get("matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/raw-data/calibration-data/long-term-counts-federal-road.txt");
	private static final Path longTermCountsRootHighway = Paths.get("matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/raw-data/calibration-data/long-term-counts-highway.txt");
	private static final Path longTermCountsIdMapping = Paths.get("matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/raw-data/calibration-data/countstation-osm-node-matching.csv");

	private static final Path outputCounts = Paths.get("matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/matsim-input-files/counts-duesseldorf.xml.gz");

	public static void main(String[] args) throws IOException {

		logger.info("Program starts!");

		var input = new InputArguments();
		JCommander.newBuilder().addObject(input).build().parse(args);

		if (!testInputFiles(input)) {
			throw new RuntimeException("NO!");
		}

		var matching = new NodeMatcher();
		var matchingResult = matching.parseNodeMatching(input.sharedsvn + longTermCountsIdMapping);

		logger.info("Finished with matching nodes.");

		var longTerm = new GetCountData();
		var longTermResult = longTerm.countData(input.sharedsvn + longTermCountsRootFederalRoad, input.sharedsvn + longTermCountsRootHighway, matchingResult);

		var counts = new Counts<Link>();
		counts.setYear(2018);

		for (var data : longTermResult.entrySet()) {

			GetCountData.CountingData value = data.getValue();

			var count = counts.createAndAddCount(Id.createLinkId(value.getLinkId()), data.getValue().getStationId());

			System.out.println(data.getValue().getStationId());

			for (var hour : data.getValue().getResult().keySet()) {

				count.createVolume(Integer.parseInt(StringUtils.stripStart(hour, "0")), data.getValue().getResult().get(hour));

			}
			logger.info("Create new count object! Station ID: " + value.getStationId() + "  Link ID: " + value.getLinkId() + "  Counts: " + value.getResult());
		}

		new CountsWriter(counts).write(input.sharedsvn + outputCounts);
	}

	private static boolean testInputFiles(InputArguments input) {
		return Files.exists(Paths.get(input.sharedsvn + longTermCountsIdMapping)) && Files.exists(Paths.get(input.sharedsvn + longTermCountsRootHighway)) && Files.exists(Paths.get(input.sharedsvn + outputCounts));
	}

	static class InputArguments {

		@Parameter(names = {"-sharedsvn", "-s"}, description = "Path to the sharedSVN folder", required = true)
		private String sharedsvn;
	}
}
