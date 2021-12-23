package org.matsim.prepare;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectReferencePair;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.contrib.sumo.SumoNetworkConverter;
import org.matsim.contrib.sumo.SumoNetworkHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.lanes.*;
import org.matsim.run.RunDuesseldorfScenario;
import org.matsim.utils.objectattributes.attributable.Attributable;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.run.RunDuesseldorfScenario.VERSION;
import static org.matsim.run.TurnDependentFlowEfficiencyCalculator.ATTR_TURN_EFFICIENCY;

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
public final class CreateNetwork implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(CreateNetwork.class);

	/**
	 * Capacities below this threshold are unplausible and ignored.
	 */
	private static final double CAPACITY_THRESHOLD = 375;

	@CommandLine.Parameters(arity = "1..*", paramLabel = "INPUT", description = "Input file", defaultValue = "scenarios/input/sumo.net.xml")
	private List<Path> input;

	@CommandLine.Option(names = "--output", description = "Output xml file", defaultValue = "scenarios/input/duesseldorf-" + VERSION + "-network.xml.gz")
	private Path output;

	@CommandLine.Option(names = "--shp", description = "Shape file used for filtering",
			defaultValue = "../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/dilutionArea/dilutionArea.shp")
	private Path shapeFile;

	@CommandLine.Option(names = "--from-osm", description = "Import from OSM without lane information", defaultValue = "false")
	private boolean fromOSM;

	@CommandLine.Option(names = {"--capacities"}, description = "CSV file with lane capacities", required = false)
	private Path capacities;

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

		SumoNetworkHandler handler = converter.convert(network, lanes);

		converter.calculateLaneCapacities(network, lanes);

		// This needs to run without errors, otherwise network is broken
		network.getLinks().values().forEach(link -> {
			LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
			if (l2l != null)
				LanesUtils.createLanes(link, l2l);
		});

		if (capacities != null) {

			Object2DoubleMap<Pair<Id<Link>, Id<Link>>> map = readLinkCapacities(capacities);

			log.info("Read lane capacities from {}, containing {} links", capacities, map.size());

			int n = setLinkCapacities(network, map);

			log.info("Unmatched links: {}", n);

		}

		applyNetworkCorrections(network);

		new NetworkWriter(network).write(output.toAbsolutePath().toString());
		new LanesWriter(lanes).write(output.toAbsolutePath().toString().replace(".xml", "-lanes.xml"));

		converter.writeGeometry(handler, output.toAbsolutePath().toString().replace(".xml", "-linkGeometries.csv").replace(".gz", ""));

		return 0;
	}

	/**
	 * Correct erroneous data from osm. Most common error is wrong number of lanes or wrong capacities.
	 */
	private void applyNetworkCorrections(Network network) {

		Map<Id<Link>, ? extends Link> links = network.getLinks();

		// Double lanes for these links
		List<String> incorrect = Lists.newArrayList(
				"-40686598#1",
				"40686598#0",
				"25494723",
				"7653201",
				"340415235",
				"34380173#0",
				"85943638",
				"18708266",
				"38873048",
				"-705697329#0",
				"-367884913",
				"93248576",
				"30694311#0",
				"432816762"
		);

		for (String l : incorrect) {
			Link link = links.get(Id.createLinkId(l));

			if (link == null || link.getNumberOfLanes() > 1) {
				log.warn("Lanes for {} not modified", l);
				continue;
			}

			link.setNumberOfLanes(link.getNumberOfLanes() * 2);
			link.setCapacity(link.getCapacity() * 2);
		}

		// Fix the capacities of some links that are implausible in OSM
		increaseCapacity(links, "314648993#0", 6000);
		increaseCapacity(links, "239242545", 3000);
		increaseCapacity(links, "800035681", 3000);
		increaseCapacity(links, "145178328", 4000);
		increaseCapacity(links, "157381200#0", 4000);
		increaseCapacity(links, "145178328", 4000);
		increaseCapacity(links, "45252320", 4000);
		increaseCapacity(links, "375678205#0", 1200);
		increaseCapacity(links, "40816222#0", 1200);
		increaseCapacity(links, "233307305#0", 1200);
		increaseCapacity(links, "23157292#0", 1200);
		increaseCapacity(links, "-33473202#1", 1200);
		increaseCapacity(links, "26014655#0", 1200);
		increaseCapacity(links, "32523335#5", 1200);

	}

	private static void increaseCapacity(Map<Id<Link>, ? extends Link> links, String id, double capacity) {

		Link link = links.get(Id.createLinkId(id));

		if (link == null || link.getCapacity() > capacity) {
			log.warn("Capacity for {} not modified", id);
			return;
		}

		link.setCapacity(capacity);
	}


	/**
	 * Read lane capacities from csv file.
	 *
	 * @return triples of fromLink, toLink, fromLane
	 */
	public static Object2DoubleMap<Triple<Id<Link>, Id<Link>, Id<Lane>>> readLaneCapacities(Path input) {

		Object2DoubleMap<Triple<Id<Link>, Id<Link>, Id<Lane>>> result = new Object2DoubleOpenHashMap<>();

		try (CSVParser parser = new CSVParser(IOUtils.getBufferedReader(input.toString()),
				CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			for (CSVRecord record : parser) {

				Id<Link> fromLinkId = Id.create(record.get("fromEdgeId"), Link.class);
				Id<Link> toLinkId = Id.create(record.get("toEdgeId"), Link.class);
				Id<Lane> fromLaneId = Id.create(record.get("fromLaneId"), Lane.class);

				Triple<Id<Link>, Id<Link>, Id<Lane>> key = Triple.of(fromLinkId, toLinkId, fromLaneId);

				result.mergeDouble(key, Integer.parseInt(record.get("intervalVehicleSum")), Double::sum);

			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return result;
	}

	/**
	 * Read link capacities from csv file.
	 * @return pair of from link, to link -> capacity
	 */
	public static Object2DoubleMap<Pair<Id<Link>, Id<Link>>> readLinkCapacities(Path input) {

		Object2DoubleMap<Pair<Id<Link>, Id<Link>>> result = new Object2DoubleOpenHashMap<>();

		try (CSVParser parser = new CSVParser(IOUtils.getBufferedReader(input.toString()),
				CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader())) {

			for (CSVRecord record : parser) {

				Id<Link> fromLinkId = Id.create(record.get("fromEdgeId"), Link.class);
				Id<Link> toLinkId = Id.create(record.get("toEdgeId"), Link.class);

				Pair<Id<Link>, Id<Link>> key = Pair.of(fromLinkId, toLinkId);

				result.put(key, Double.parseDouble(record.get("flow")));

			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return result;
	}

	/**
	 * Aggregate maximum lane capacities, independent of turning direction.
	 */
	public static Object2DoubleMap<Pair<Id<Link>, Id<Lane>>> calcMaxLaneCapacities(Object2DoubleMap<Triple<Id<Link>, Id<Link>, Id<Lane>>> map) {

		Object2DoubleMap<Pair<Id<Link>, Id<Lane>>> laneCapacities = new Object2DoubleOpenHashMap<>();

		// sum for each link
		for (Object2DoubleMap.Entry<Triple<Id<Link>, Id<Link>, Id<Lane>>> e : map.object2DoubleEntrySet()) {
			laneCapacities.mergeDouble(ObjectReferencePair.of(e.getKey().getLeft(), e.getKey().getRight()), e.getDoubleValue(), Double::max);
		}

		return laneCapacities;
	}

	/**
	 * Use provided link capacities and apply them to the network.
	 *
	 * @return number of links from file that are not in the network.
	 */
	public static int setLinkCapacities(Network network, Object2DoubleMap<Pair<Id<Link>, Id<Link>>> map) {

		Object2DoubleMap<Id<Link>> linkCapacities = new Object2DoubleOpenHashMap<>();

		// max of each link
		for (Object2DoubleMap.Entry<Pair<Id<Link>, Id<Link>>> e : map.object2DoubleEntrySet()) {
			linkCapacities.mergeDouble(e.getKey().key(), e.getDoubleValue(), Double::max);
		}

		int unmatched = 0;

		for (Object2DoubleMap.Entry<Id<Link>> e : linkCapacities.object2DoubleEntrySet()) {

			Link link = network.getLinks().get(e.getKey());

			if (link != null) {
				// ignore unplausible capacities
				if (e.getDoubleValue() < CAPACITY_THRESHOLD * link.getNumberOfLanes())
					continue;

				link.setCapacity(e.getDoubleValue());
				link.getAttributes().putAttribute("junction", true);
			} else {
				unmatched++;
			}
		}

		// set turn capacities relative to whole link capacity
		for (Object2DoubleMap.Entry<Pair<Id<Link>, Id<Link>>> e : map.object2DoubleEntrySet()) {

			Id<Link> fromLink = e.getKey().left();
			Id<Link> toLink = e.getKey().right();

			Link link = network.getLinks().get(fromLink);

			if (link == null)
				continue;

			double cap = Math.max(CAPACITY_THRESHOLD, e.getDoubleValue());
			getTurnEfficiencyMap(link).put(toLink.toString(), String.valueOf(cap / link.getCapacity()));
		}


		propagateJunctionCapacities(network);

		return unmatched;
	}

	/**
	 * Apply the capacities at intersection to up- and downstream links if applicable.
	 */
	private static void propagateJunctionCapacities(Network network) {

		Set<Id<Link>> handled = new HashSet<>();

		// First pass, apply downstream
		for (Link link : network.getLinks().values()) {

			if (link.getAttributes().getAttribute("junction") != Boolean.TRUE)
				continue;

			double cap = link.getCapacity();

			Node from = link.getFromNode();
			while (from.getOutLinks().size() == 1 && from.getInLinks().size() == 1) {
				for (Link inLink : from.getInLinks().values()) {
					handled.add(inLink.getId());
					if (inLink.getCapacity() < cap) {
						inLink.setCapacity(cap);
					}

					from = inLink.getFromNode();
				}
			}
		}

		// Second pass, apply min required capacity upstream
		for (Link link : network.getLinks().values()) {

			if (link.getAttributes().getAttribute("junction") != Boolean.TRUE || handled.contains(link.getId()))
				continue;

			Node to = link.getToNode();

			double cap = to.getInLinks().values().stream().mapToDouble(Link::getCapacity).min().orElse(0);

			Queue<Link> queue = new LinkedList<>(to.getOutLinks().values());

			while (!queue.isEmpty()) {

				Link outLink = queue.poll();
				if (handled.contains(outLink.getId()))
					continue;

				handled.add(outLink.getId());

				if (outLink.getCapacity() < cap)
					outLink.setCapacity(cap);

				// Capacity is only applied as long as there is no other intersection
				if (outLink.getToNode().getOutLinks().size() == 1)
					queue.addAll(outLink.getToNode().getOutLinks().values());

			}
		}
	}

	/**
	 * Use provided lane capacities, to calculate aggregated capacities for all links.
	 * This does not modify lane capacities.
	 *
	 * @return number of links from file that are not in the network.
	 */
	public static int setLinkCapacitiesFromLaneMap(Network network, Object2DoubleMap<Triple<Id<Link>, Id<Link>, Id<Lane>>> map) {

		Object2DoubleMap<Id<Link>> linkCapacities = new Object2DoubleOpenHashMap<>();
		Object2DoubleMap<Pair<Id<Link>, Id<Lane>>> laneCapacities = calcMaxLaneCapacities(map);

		// sum for each link
		for (Object2DoubleMap.Entry<Pair<Id<Link>, Id<Lane>>> e : laneCapacities.object2DoubleEntrySet()) {
			linkCapacities.mergeDouble(e.getKey().key(), e.getDoubleValue(), Double::sum);
		}

		int unmatched = 0;

		for (Object2DoubleMap.Entry<Id<Link>> e : linkCapacities.object2DoubleEntrySet()) {

			Link link = network.getLinks().get(e.getKey());

			if (link != null) {
				// ignore unplausible capacities
				if (e.getDoubleValue() < CAPACITY_THRESHOLD * link.getNumberOfLanes())
					continue;

				link.setCapacity(e.getDoubleValue());
				link.getAttributes().putAttribute("junction", true);
			} else {
				unmatched++;
			}
		}

		Object2DoubleMap<Pair<Id<Link>, Id<Link>>> turnCapacities = new Object2DoubleOpenHashMap<>();

		for (Object2DoubleMap.Entry<Triple<Id<Link>, Id<Link>, Id<Lane>>> e : map.object2DoubleEntrySet()) {
			turnCapacities.mergeDouble(Pair.of(e.getKey().getLeft(), e.getKey().getMiddle()), e.getDoubleValue(), Double::sum);
		}

		// set turn capacities relative to whole link capacity
		for (Object2DoubleMap.Entry<Pair<Id<Link>, Id<Link>>> e : turnCapacities.object2DoubleEntrySet()) {

			Id<Link> fromLink = e.getKey().left();
			Id<Link> toLink = e.getKey().right();

			Link link = network.getLinks().get(fromLink);

			if (link == null)
				continue;

			getTurnEfficiencyMap(link).put(toLink.toString(), String.valueOf(e.getDoubleValue() / link.getCapacity()));
		}


		return unmatched;
	}

	/**
	 * Use provided lane capacities and apply them in the network.
	 *
	 * @return number of lanes in file, but not in the network.
	 */
	public static int setLaneCapacities(Lanes lanes, Object2DoubleMap<Triple<Id<Link>, Id<Link>, Id<Lane>>> map) {

		Object2DoubleMap<Pair<Id<Link>, Id<Lane>>> laneCapacities = calcMaxLaneCapacities(map);

		int unmatched = 0;

		SortedMap<Id<Link>, LanesToLinkAssignment> l2ls = lanes.getLanesToLinkAssignments();

		for (Object2DoubleMap.Entry<Pair<Id<Link>, Id<Lane>>> e : laneCapacities.object2DoubleEntrySet()) {

			LanesToLinkAssignment l2l = l2ls.get(e.getKey().key());

			if (l2l == null) {
				unmatched++;
				continue;
			}

			Lane lane = l2l.getLanes().get(e.getKey().right());

			if (lane == null) {
				unmatched++;
				continue;
			}

			// ignore unplausible capacities
			if (e.getDoubleValue() < CAPACITY_THRESHOLD)
				continue;

			lane.setCapacityVehiclesPerHour(e.getDoubleValue());
		}

		// set turn efficiency depending on to link
		for (Object2DoubleMap.Entry<Triple<Id<Link>, Id<Link>, Id<Lane>>> e : map.object2DoubleEntrySet()) {

			LanesToLinkAssignment l2l = l2ls.get(e.getKey().getLeft());
			if (l2l == null) continue;

			Lane lane = l2l.getLanes().get(e.getKey().getRight());
			if (lane == null) continue;

			Id<Link> toLink = e.getKey().getMiddle();
			getTurnEfficiencyMap(lane).put(toLink.toString(), String.valueOf(e.getDoubleValue() / lane.getCapacityVehiclesPerHour()));
		}


		return unmatched;
	}

	/**
	 * Retrieves turn efficiency from attributes.
	 */
	private static Map<String, String> getTurnEfficiencyMap(Attributable obj) {
		Map<String, String> cap = (Map<String, String>) obj.getAttributes().getAttribute(ATTR_TURN_EFFICIENCY);

		if (cap == null) {
			cap = new HashMap<>();
			obj.getAttributes().putAttribute(ATTR_TURN_EFFICIENCY, cap);
		} else if (cap.getClass().getName().contains("Unmodifiable")) {
			// copy the map
			cap = new HashMap<>(cap);
			obj.getAttributes().putAttribute(ATTR_TURN_EFFICIENCY, cap);
		}

		return cap;
	}

}
