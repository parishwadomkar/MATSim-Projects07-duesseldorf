package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
		name = "extract-intersections",
		description = "Extract relevant signalized intersections for a network"
)
public class ExtractIntersections implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(ExtractIntersections.class);

	@CommandLine.Parameters(arity = "0..1", paramLabel = "INPUT", description = "Path to network",
			defaultValue = "scenarios/input/duesseldorf-v1.5-network.xml.gz")
	private Path network;

	@CommandLine.Mixin
	private ShpOptions shp = new ShpOptions(
			Path.of("../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp"),
			null, null);

	@CommandLine.Option(names = "--output", description = "Output path", defaultValue = "intersections.txt")
	private Path output;

	public static void main(String[] args) {
		new ExtractIntersections().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(network)) {
			log.error("Input network {} does not exists", network);
			return 2;
		}

		Network network = NetworkUtils.readTimeInvariantNetwork(this.network.toString());

		// buffer around the network
		Geometry geom = shp.getGeometry().getEnvelope().buffer(8000);

		int filtered = 0;

		try (BufferedWriter writer = Files.newBufferedWriter(output)) {
			for (Node node : network.getNodes().values()) {
				if (geom.contains(MGC.coord2Point(node.getCoord()))) {
					writer.write(node.getId().toString());
					writer.write("\n");
					filtered++;
				}
			}
		}


		log.info("Written {} out of {} ({})", filtered, network.getNodes().size(), (filtered * 100d / network.getNodes().size()));

		return 0;
	}
}
