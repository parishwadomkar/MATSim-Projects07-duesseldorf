package org.matsim.prepare;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.sumo.SumoNetworkConverter;
import org.matsim.contrib.sumo.SumoNetworkHandler;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.LanesReader;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesWriter;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Executable class to extract a sub network.
 */
@CommandLine.Command(
        name = "extractNetwork",
        description = "Extract network and lanes for certain area.",
        showDefaultValues = true
)
@Deprecated
public class ExtractNetwork implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(ExtractNetwork.class);

    @CommandLine.Option(names = "--network", description = "Path to the SUMO network to extract events for", required = true)
    private Path network;

    @CommandLine.Option(names = "--lanes", description = "Path to lanes files", defaultValue = "guess", required = true)
    private Path lanes;

    @CommandLine.Option(names = "--sumo-network", description = "Path to the SUMO network to extract events for", required = true)
    private Path sumoNetwork;

    @CommandLine.Option(names = "--output", description = "Path to output file", required = true)
    private Path output;

    private SumoNetworkHandler sumo;
    private List<Event> events;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ExtractNetwork()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        sumo = SumoNetworkConverter.readNetwork(sumoNetwork.toFile());

        Network network = NetworkUtils.readNetwork(this.network.toString());

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        LanesReader lanesReader = new LanesReader(scenario);
        lanesReader.readFile(lanes.getFileName().toString().equals("guess") ?
                this.network.toString().replace(".xml", "-lanes.xml") : lanes.toString());

        log.info("Read {} links and {} lanes", network.getLinks().size(), scenario.getLanes().getLanesToLinkAssignments().size());

        for (Node node : Lists.newArrayList(network.getNodes().values())) {
            if (!sumo.getJunctions().containsKey(node.getId().toString()))
                network.removeNode(node.getId());
        }

        // copy relevant lanes
        Scenario laneScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        for (Map.Entry<Id<Link>, LanesToLinkAssignment> e : scenario.getLanes().getLanesToLinkAssignments().entrySet()) {
            if (network.getLinks().containsKey(e.getKey()))
                laneScenario.getLanes().addLanesToLinkAssignment(e.getValue());
        }

        NetworkUtils.writeNetwork(network, output.toString());
        new LanesWriter(laneScenario.getLanes())
                .write(output.toString().replace(".xml", "-lanes.xml"));

        return 0;
    }
}
