package org.matsim.analyze.travelTimeAnalysis;

import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.analysis.vsp.traveltimedistance.HereMapsRouteValidator;
import org.matsim.contrib.analysis.vsp.traveltimedistance.TravelTimeValidationRunner;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

public class RunTravelTimeAnalysis {

	/**
	 *
	 * @param args Arguments to pass:
	 *             <ol type="1">
	 *             <li>A MATSim Plans file</li>
	 *             <li>A MATSim Events file</li>
	 *             <li>A MATSim Network file</li>
	 *             <li>EPSG-Code of your coordinate system</li>
	 *             <li>HERE Maps APP code, see here.com</li>
	 *             <li>Output folder location</li>
	 *             <li>The date to validate travel times for, format:
	 *             YYYY-MM-DD</li>
	 *
	 *             <li>(Optional: The number of trips to validate)</li>
	 *
	 *             </ol>
	 *
	 */
	public static void main(String[] args) {
		String plans = args[0];
		String events = args[1];
		String network = args[2];
		String epsg = args[3];
		String apiAccessKey = args[4];
		String outputfolder = args[5];
		String date = args[6];
		Integer tripsToValidate = null;
		Double timeWindowStart = null;
		Double timeWindowEnd = null;
		if (args.length > 7) {
			tripsToValidate = Integer.parseInt(args[7]);
		}

		if (args.length > 8) {
			timeWindowStart = Double.parseDouble(args[8]);
			timeWindowEnd = Double.parseDouble(args[9]);
		}

		Set<Id<Person>> populationIds = new HashSet<>();
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(network);
		StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
		spr.addAlgorithm(person -> populationIds.add(person.getId()));
		spr.readFile(plans);
		System.out.println("populationId Size is " + populationIds.size());

		CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(epsg,
				TransformationFactory.WGS84);
		HereMapsRouteValidator validator = new HereMapsRouteValidator(outputfolder, apiAccessKey, date, transformation);
		// Setting this to true will write out the raw JSON files for each calculated
		// route
		validator.setWriteDetailedFiles(false);
		TravelTimeValidationRunner runner;

		if (tripsToValidate != null) {
			if (timeWindowStart != null) {
				Tuple<Double, Double> timeWindow = new Tuple<Double, Double>((double) timeWindowStart,
						(double) timeWindowEnd);
				runner = new TravelTimeValidationRunner(scenario.getNetwork(), populationIds, events, outputfolder,
						validator, tripsToValidate, timeWindow);
			} else {
				runner = new TravelTimeValidationRunner(scenario.getNetwork(), populationIds, events, outputfolder,
						validator, tripsToValidate);
			}
		} else {
			runner = new TravelTimeValidationRunner(scenario.getNetwork(), populationIds, events, outputfolder,
					validator);
		}

		runner.run();

	}
}
