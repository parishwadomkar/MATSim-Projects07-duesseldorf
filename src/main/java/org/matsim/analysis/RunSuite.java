package org.matsim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.run.RunDuesseldorfScenario;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "runSuite",
		description = "Run suite of analysis functionality."
)
public class RunSuite implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(RunSuite.class);

	@CommandLine.Parameters(arity = "1..2", paramLabel = "INPUT", description = "Input run directory. Two Runs")
	private List<Path> runDirectory;

	@CommandLine.Option(names = "--run-id", defaultValue = "*", description = "Pattern used to match runId", required = true)
	private String runId;

	@CommandLine.Option(names = "--run-id-compare", defaultValue = "", description = "Run id to compare with", required = false)
	private String runIdToCompareWith;

	@CommandLine.Option(names = "--shp", required = true, description = "Shapefile used as trip filter",
			defaultValue = "../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp")
	private Path shapeFile;


	public static void main(String[] args) {
		System.exit(new CommandLine(new RunSuite()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		final String[] helpLegModes = {TransportMode.walk}; // to be able to analyze old runs
		final int scalingFactor = 4;
		final String homeActivityPrefix = "home";
		final String modesString = TransportMode.car + "," + TransportMode.pt + "," + TransportMode.bike + "," + TransportMode.walk + "," + TransportMode.ride;


		Scenario scenario1 = loadScenario(runId, runDirectory.get(0));
		Scenario scenario0 = null;

		if (runDirectory.size() > 1)
			scenario0 = loadScenario(runIdToCompareWith, runDirectory.get(1));

		List<AgentFilter> agentFilters = new ArrayList<>();

		AgentAnalysisFilter filter1a = new AgentAnalysisFilter("");
		filter1a.preProcess(scenario1);
		agentFilters.add(filter1a);

		AgentAnalysisFilter filter1b = new AgentAnalysisFilter("residents-in-area");
		filter1b.setZoneFile(shapeFile.toString());
		filter1b.setRelevantActivityType(homeActivityPrefix);
		filter1b.preProcess(scenario1);
		agentFilters.add(filter1b);

		List<TripFilter> tripFilters = new ArrayList<>();

		TripAnalysisFilter tripFilter1a = new TripAnalysisFilter("");
		tripFilter1a.preProcess(scenario1);
		tripFilters.add(tripFilter1a);

		TripAnalysisFilter tripFilter1b = new TripAnalysisFilter("o-and-d-in-area");
		tripFilter1b.setZoneInformation(shapeFile.toString(), RunDuesseldorfScenario.COORDINATE_SYSTEM);
		tripFilter1b.preProcess(scenario1);
		tripFilter1b.setBuffer(0.);
		tripFilter1b.setTripConsiderType(TripAnalysisFilter.TripConsiderType.OriginAndDestination);
		tripFilters.add(tripFilter1b);

		final List<VehicleFilter> vehicleFilters = new ArrayList<>();

		vehicleFilters.add(null);

		VehicleAnalysisFilter vehicleAnalysisFilter1 = new VehicleAnalysisFilter("drt-vehicles", "drt", VehicleAnalysisFilter.StringComparison.Contains);
		vehicleFilters.add(vehicleAnalysisFilter1);

		VehicleAnalysisFilter vehicleAnalysisFilter2 = new VehicleAnalysisFilter("pt-vehicles", "tr", VehicleAnalysisFilter.StringComparison.Contains);
		vehicleFilters.add(vehicleAnalysisFilter2);

		List<String> modes = Arrays.asList(modesString.split(","));

		MatsimAnalysis analysis = new MatsimAnalysis();
		analysis.setScenario1(scenario1);
		analysis.setScenario0(scenario0);

		analysis.setAgentFilters(agentFilters);
		analysis.setTripFilters(tripFilters);
		analysis.setVehicleFilters(vehicleFilters);

		analysis.setScenarioCRS(RunDuesseldorfScenario.COORDINATE_SYSTEM);
		analysis.setScalingFactor(scalingFactor);
		analysis.setModes(modes);
		analysis.setHelpLegModes(helpLegModes);
		analysis.setZoneInformation(shapeFile.toString(), RunDuesseldorfScenario.COORDINATE_SYSTEM, null);
		analysis.setVisualizationScriptInputDirectory(null);

		analysis.run();

		return 0;
	}

	/**
	 * Glob pattern from path, if not found tries to go into the parent directory.
	 */
	static Optional<Path> glob(Path path, String pattern) {
		PathMatcher m = path.getFileSystem().getPathMatcher("glob:" + pattern);
		try {
			Optional<Path> match = Files.list(path).filter(p -> m.matches(p.getFileName())).findFirst();
			// Look one directory higher for required file
			if (match.isEmpty())
				return Files.list(path.getParent()).filter(p -> m.matches(p.getFileName())).findFirst();

			return match;
		} catch (IOException e) {
			log.warn(e);
		}

		return Optional.empty();
	}

	/**
	 * Load scenario using globed patterns.
	 */
	static Scenario loadScenario(String runId, Path runDirectory) {
		log.info("Loading scenario...");

		Path populationFile = glob(runDirectory, runId + ".*plans.*").orElseThrow(() -> new IllegalStateException("No plans file found."));
		int index = populationFile.getFileName().toString().indexOf(".");
		if (index == -1)
			index = 0;

		String resolvedRunId = populationFile.getFileName().toString().substring(0, index);
		log.info("Using population {} with run id {}", populationFile, resolvedRunId);

		Path networkFile = glob(runDirectory, runId + ".*network.*").orElseThrow(() -> new IllegalStateException("No network file found."));
		log.info("Using network {}", networkFile);

		String facilitiesFile = glob(runDirectory, runId + ".*facilities.*").map(Path::toString).orElse(null);
		log.info("Using facilities {}", facilitiesFile);

		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem(RunDuesseldorfScenario.COORDINATE_SYSTEM);
		config.controler().setOutputDirectory(runDirectory.toString());
		config.controler().setRunId(resolvedRunId);

		config.plans().setInputFile(populationFile.toString());
		config.network().setInputFile(networkFile.toString());
		config.facilities().setInputFile(facilitiesFile);

		return ScenarioUtils.loadScenario(config);
	}
}
