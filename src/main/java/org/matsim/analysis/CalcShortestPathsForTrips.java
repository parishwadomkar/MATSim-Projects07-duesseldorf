package org.matsim.analysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class CalcShortestPathsForTrips {
	public static int findIndex(String arr[], String t)
	{
		// Creating ArrayList
		ArrayList<String> clist = new ArrayList<>();

		// adding elements of array
		// to ArrayList
		for (String i : arr)
			clist.add(i);

		// returning index of the element
		return clist.indexOf(t);
	}
	public static void main(String[] args) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Network network = scenario.getNetwork();
		new MatsimNetworkReader(network).readFile(args[0]);
		BufferedReader reader = IOUtils.getBufferedReader(args[1]);
		File path = new File(args[1]).getParentFile();
		BufferedWriter writer = IOUtils.getBufferedWriter(path.getAbsolutePath().toString() + "/dd.output_trip_shortest_path_distances.csv.gz");
		ShortestPathCalculator calculator = new ShortestPathCalculator(network);
		try {
			writer.write("id,walkdistance\n");
			String line = reader.readLine();
			int tripid_idx = findIndex(line.split(";"), "trip_id");
			int start_link_idx = findIndex(line.split(";"), "start_link");
			int end_link_idx = findIndex(line.split(";"), "end_link");
			line = reader.readLine();
			int i =0;
			while (line != null) {
				System.out.println(i++);
				String[] split = line.split(";");
				Id<Link> startLinkId = Id.createLinkId(split[start_link_idx]);
				Id<Link> endLinkId = Id.createLinkId(split[end_link_idx]);
				Node startNode = network.getLinks().get(startLinkId).getToNode();
				Node endNode = network.getLinks().get(endLinkId).getToNode();
				double distance = calculator.getPath(startNode, endNode, 0).travelTime;
				writer.write(split[tripid_idx]+","+distance+"\n");
				line = reader.readLine();
			}
			writer.flush();
			writer.close();
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}


class ShortestPathCalculator {
	final Network network;
	TravelDisutilityFactory disutilityFactory;
	TravelTime travelTime;
	LeastCostPathCalculator dijkstra;

	// define how the travel disutility is computed:

	ShortestPathCalculator(Network network) {
		this.network = network;
		travelTime = new WalkingTravelTime();
		disutilityFactory = new OnlyTimeDependentTravelDisutilityFactory();
		dijkstra = new DijkstraFactory().createPathCalculator(network,
				disutilityFactory.createTravelDisutility(travelTime), travelTime
		);
	}

	LeastCostPathCalculator.Path getPath(Node fromNode, Node toNode, double time) {

		return dijkstra.calcLeastCostPath(fromNode, toNode, time, null, null);
	}

}

class WalkingTravelTime implements TravelTime {

	@Override
	public double getLinkTravelTime(Link link, double v, Person person, Vehicle vehicle) {
		return link.getLength();
	}
}
