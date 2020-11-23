package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.contrib.sumo.SumoNetworkConverter;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;
import org.matsim.lanes.LanesWriter;
import org.matsim.run.RunDuesseldorfScenario;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

import static org.matsim.run.RunDuesseldorfScenario.VERSION;

/**
 * Creates the road network layer.
 * <p>
 * Use https://download.geofabrik.de/europe/germany/nordrhein-westfalen/duesseldorf-regbez-latest.osm.pbf
 *
 * @author rakow
 */
@CommandLine.Command(
		name = "network",
		description = "Create MATSim network from OSM data",
		showDefaultValues = true
)
public class CreateNetwork implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(CreateNetwork.class);

	@CommandLine.Parameters(arity = "1..*", paramLabel = "INPUT", description = "Input file", defaultValue = "scenarios/input/sumo.net.xml")
	private List<Path> input;

	@CommandLine.Option(names = "--output", description = "Output xml file", defaultValue = "scenarios/input/duesseldorf-" + VERSION + "-network.xml.gz")
	private Path output;

	@CommandLine.Option(names = "--shp", description = "Shape file used for filtering",
			defaultValue = "../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/dilutionArea/dilutionArea.shp")
	private Path shapeFile;

	@CommandLine.Option(names = "--from-osm", description = "Import from OSM without lane information", defaultValue = "false")
	private boolean fromOSM;

	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateNetwork()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (fromOSM) {

			CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, RunDuesseldorfScenario.COORDINATE_SYSTEM);

			Network network = new SupersonicOsmNetworkReader.Builder()
					.setCoordinateTransformation(ct)
					.setIncludeLinkAtCoordWithHierarchy((coord, hierachyLevel) ->
							hierachyLevel <= LinkProperties.LEVEL_RESIDENTIAL &&
									coord.getX() >= RunDuesseldorfScenario.X_EXTENT[0] && coord.getX() <= RunDuesseldorfScenario.X_EXTENT[1] &&
									coord.getY() >= RunDuesseldorfScenario.Y_EXTENT[0] && coord.getY() <= RunDuesseldorfScenario.Y_EXTENT[1]
					)

					.setAfterLinkCreated((link, osmTags, isReverse) -> link.setAllowedModes(new HashSet<>(Arrays.asList(TransportMode.car, TransportMode.bike, TransportMode.ride))))
					.build()
					.read(input.get(0));

			new NetworkWriter(network).write(output.toAbsolutePath().toString());

			return 0;
		}

		SumoNetworkConverter converter = SumoNetworkConverter.newInstance(input, output, shapeFile, "EPSG:32632", RunDuesseldorfScenario.COORDINATE_SYSTEM);

		Network network = NetworkUtils.createNetwork();
		Lanes lanes = LanesUtils.createLanesContainer();

		converter.convert(network, lanes);

		converter.calculateLaneCapacities(network, lanes);

		// This needs to run without errors, otherwise network is broken
		network.getLinks().values().forEach(link -> {
			LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
			if (l2l != null)
				LanesUtils.createLanes(link, l2l);
		});

		new NetworkWriter(network).write(output.toAbsolutePath().toString());
		new LanesWriter(lanes).write(output.toAbsolutePath().toString().replace(".xml", "-lanes.xml"));

		return 0;
	}
}
