package org.matsim.analyze;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.analysis.modalSplitUserType.ModeAnalysis;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.run.RunDuesseldorfScenario;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "modeAnalysis",
		description = "Run mode analysis on a specific run."
)
public class ModeAnalysisWithHomeLocationFilter implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(ModeAnalysisWithHomeLocationFilter.class);

	@CommandLine.Parameters(arity = "0..1", paramLabel = "INPUT", description = "Input run directory",
			defaultValue = "C:/Users/cluac/MATSimScenarios/Dusseldorf/output/S510")
	private Path runDirectory;

	@CommandLine.Option(names = "--run-id", defaultValue = "*", description = "Pattern used to match runId", required = true)
	private String runId;

	@CommandLine.Option(names = "--output", defaultValue = "modeAnalysisResults", required = true)
	private String output;

	@CommandLine.Option(names = "--shp", required = true,
			defaultValue = "../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp")
	private Path shapeFile;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ModeAnalysisWithHomeLocationFilter()).execute(args));
	}

	static Optional<Path> glob(Path path, String pattern) throws IOException {
		PathMatcher m = path.getFileSystem().getPathMatcher("glob:" + pattern);
		Optional<Path> match = Files.list(path).filter(p -> m.matches(p.getFileName())).findFirst();

		// Look one directory higher for required file
		if (match.isEmpty())
			return Files.list(path.getParent()).filter(p -> m.matches(p.getFileName())).findFirst();

		return match;
	}

	@Override
	public Integer call() throws Exception {
		final AnalysisMainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

		if (!Files.exists(runDirectory)) {
			log.error("Run directory {} does not exists.", runDirectory);
			return 1;
		}

		Scenario scenario = loadScenario(runId, runDirectory);

		HomeLocationFilter homeLocationFilter = new HomeLocationFilter(shapeFile.toString());
		homeLocationFilter.analyzePopulation(scenario.getPopulation());

		ModeAnalysis modeAnalysis = new ModeAnalysis(scenario, homeLocationFilter, mainModeIdentifier);

		modeAnalysis.run();
		writeResults(runDirectory.resolve(output), modeAnalysis);

		return 0;
	}

	static Scenario loadScenario(String runId, Path runDirectory) throws IOException {
		log.info("Loading scenario...");

		Path populationFile = glob(runDirectory,  runId+".*plans.*").orElseThrow(() -> new IllegalStateException("No plans file found."));
		log.info("Using population {}", populationFile);

		Path networkFile = glob(runDirectory,  runId + ".*network.*").orElseThrow(() -> new IllegalStateException("No network file found."));
		log.info("Using network {}", networkFile);

		String facilitiesFile = glob(runDirectory, runId + ".*facilities.*").map(Path::toString).orElse(null);
		log.info("Using facilities {}", facilitiesFile);

		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem(RunDuesseldorfScenario.COORDINATE_SYSTEM);
		config.controler().setOutputDirectory(runDirectory.toString());

		config.plans().setInputFile(populationFile.toString());
		config.network().setInputFile(networkFile.toString());
		config.facilities().setInputFile(facilitiesFile);

		return ScenarioUtils.loadScenario(config);
	}

	private static void writeResults(Path analysisOutputDirectory, ModeAnalysis modeAnalysis) throws IOException {

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
