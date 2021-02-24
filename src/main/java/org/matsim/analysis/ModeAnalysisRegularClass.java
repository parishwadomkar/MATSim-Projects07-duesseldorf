package org.matsim.analysis;

import static org.matsim.analysis.RunSuite.loadScenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.matsim.analysis.modalSplitUserType.ModeAnalysis;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.utils.collections.Tuple;

public class ModeAnalysisRegularClass {
	private final Path runDirectory;
	private final String runId;
	private final String shapeFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/"
			+ "scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/"
			+ "duesseldorf-area-shp/duesseldorf-area.shp";

	public ModeAnalysisRegularClass(Path runDirectory, String runId) {
		this.runDirectory = runDirectory;
		this.runId = runId;
	}

	public void run() throws IOException {
		final AnalysisMainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

		Scenario scenario = loadScenario(runId, runDirectory);

		HomeLocationFilter homeLocationFilter = new HomeLocationFilter(shapeFile);
		homeLocationFilter.analyzePopulation(scenario.getPopulation());

		ModeAnalysis modeAnalysis = new ModeAnalysis(scenario, homeLocationFilter, null, mainModeIdentifier);

		modeAnalysis.run();
		String output = "mode-analysis-results";
		writeResults(runDirectory.resolve(output), modeAnalysis);
	}

	private void writeResults(Path analysisOutputDirectory, ModeAnalysis modeAnalysis) throws IOException {

		Files.createDirectories(analysisOutputDirectory);

		String modeAnalysisOutputDirectory = analysisOutputDirectory.toString() + "/";

		modeAnalysis.writeModeShares(modeAnalysisOutputDirectory);
		modeAnalysis.writeTripRouteDistances(modeAnalysisOutputDirectory);
		modeAnalysis.writeTripEuclideanDistances(modeAnalysisOutputDirectory);

		// mode share for different distance groups
		final List<Tuple<Double, Double>> distanceGroups1 = new ArrayList<>();
		distanceGroups1.add(new Tuple<>(0., 1000.));
		distanceGroups1.add(new Tuple<>(1000., 3000.));
		distanceGroups1.add(new Tuple<>(3000., 5000.));
		distanceGroups1.add(new Tuple<>(5000., 10000.));
		distanceGroups1.add(new Tuple<>(10000., 999999999999.));
		modeAnalysis.writeTripRouteDistances(modeAnalysisOutputDirectory + "DistanceGroup_1-3-5-10", distanceGroups1);
		modeAnalysis.writeTripEuclideanDistances(modeAnalysisOutputDirectory + "DistanceGroup_1-3-5-10",
				distanceGroups1);

		final List<Tuple<Double, Double>> distanceGroups2 = new ArrayList<>();
		distanceGroups2.add(new Tuple<>(0., 1000.));
		distanceGroups2.add(new Tuple<>(1000., 2000.));
		distanceGroups2.add(new Tuple<>(2000., 3000.));
		distanceGroups2.add(new Tuple<>(3000., 4000.));
		distanceGroups2.add(new Tuple<>(4000., 5000.));
		distanceGroups2.add(new Tuple<>(5000., 6000.));
		distanceGroups2.add(new Tuple<>(6000., 7000.));
		distanceGroups2.add(new Tuple<>(7000., 8000.));
		distanceGroups2.add(new Tuple<>(8000., 9000.));
		distanceGroups2.add(new Tuple<>(9000., 999999999999.));
		modeAnalysis.writeTripRouteDistances(modeAnalysisOutputDirectory + "DistanceGroup_1-2-3-4.", distanceGroups2);
		modeAnalysis.writeTripEuclideanDistances(modeAnalysisOutputDirectory + "DistanceGroup_1-2-3-4.",
				distanceGroups2);

	}
}
