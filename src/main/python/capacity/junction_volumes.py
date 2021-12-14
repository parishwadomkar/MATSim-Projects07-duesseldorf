#!/usr/bin/env python
# @author  Angelo Banse, Ronald Nippold, Christian Rakow

import sys
from os.path import join, basename

from utils import init_env, create_args, write_scenario, filter_network

init_env()

import traci  # noqa
import sumolib.net
from sumolib import checkBinary  # noqa
import lxml.etree as ET

import pandas as pd

sumoBinary = checkBinary('sumo')
netconvert = checkBinary('netconvert')


def writeRouteFile(f_name, departLane, arrivalLane, edges, qCV, qAV, qACV):
    text = """<?xml version="1.0" encoding="UTF-8"?>

<routes xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/routes_file.xsd">

    <vTypeDistribution id="vDist">
        <vType id="vehCV" probability="{qCV}" color="1,0,0" vClass="passenger"/>
        <vType id="vehACV" probability="{qACV}" color="0,0,1" vClass="passenger" minGap="0.5" accel="2.6" decel="3.5" sigma="0" tau="0.6" speedFactor="1" speedDev="0"/>
        <vType id="vehAV" probability="{qAV}" color="0,1,0" vClass="passenger" decel="3.0" sigma="0.1" tau="1.5" speedFactor="1" speedDev="0"/>
    </vTypeDistribution>

    <flow id="veh" begin="0" end= "3600" vehsPerHour="5000" type="vDist" departLane="best" arrivalLane="{arrivalLane}" departSpeed="max">
        <route edges="{edges}"/>
    </flow>
</routes>

"""
    context = {
        "departLane": departLane,
        "arrivalLane": arrivalLane,
        "edges": edges,
        "qCV": qCV,
        "qAV": qAV,
        "qACV": qACV
    }
    with open(f_name, "w") as f:
        f.write(text.format(**context))


def writeDetectorFile(f_name, lane):
    text = """<?xml version="1.0" encoding="UTF-8"?>

<additional xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/additional_file.xsd">
    <e1Detector id="detector" lane="{lane}" pos="0" friendlyPos="true" freq="10.00" file="{output_file}.xml"/>
</additional>

"""
    context = {
        "lane": lane,
        "output_file": "detector"
    }
    with open(f_name, 'w') as f:
        f.write(text.format(**context))


def read_result(f, **kwargs):
    total = 0
    end = 0

    for _, elem in ET.iterparse(f, events=("end",),
                                tag=('interval',),
                                remove_blank_text=True):

        begin = float(elem.attrib["begin"])
        end = float(elem.attrib["end"])
        if begin < 60:
            continue

        total += float(elem.attrib["nVehContrib"])

    kwargs["flow"] = total * (3660 / (end - 60))
    kwargs["count"] = total
    return kwargs


def run(args, nodes):
    total = args.cv + args.av + args.acv

    qCV = (args.cv / total)
    qAV = (args.av / total)
    qACV = (args.acv / total)

    print("Running vehicle shares cv: %.2f, av: %.2f, acv: %.2f" % (qCV, qAV, qACV))

    if args.to_index <= 0:
        args.to_index = len(nodes)

    i = 0

    for x in range(args.from_index, args.to_index):
        node = nodes[x]
        i += 1

        print("####################################################################")
        print("Junction id: " + node._id)

        p_network = join(args.runner, "filtered.net.xml")

        edges = [c.getFrom() for c in node.getConnections()] + [c.getTo() for c in node.getConnections()]

        filter_network(netconvert, args.network, edges, p_network, ["--no-internal-links", "false"])

        res = []

        for connection in node.getConnections():
            fromEdge = connection._from._id  # edge - string
            departLane = connection._fromLane.getIndex()  # lane - int
            toEdge = connection._to._id  # edge - string
            arrivalLane = connection._toLane.getIndex()  # lane - int

            edges = fromEdge + " " + toEdge
            lane = toEdge + "_" + str(arrivalLane)

            p_scenario = join(args.runner, "scenario.sumocfg")
            p_routes = join(args.runner, "route.rou.xml")
            p_detector = join(args.runner, "detector.add.xml")

            writeRouteFile(p_routes, departLane, arrivalLane, edges, qCV, qAV, qACV)
            writeDetectorFile(p_detector, lane)

            write_scenario(p_scenario, basename(p_network), basename(p_routes), basename(p_detector), args.step_length, time=3600)

            go(p_scenario, args)

            # Read output
            res.append(
                read_result(join(args.runner, "detector.xml"),
                            junctionId=node._id,
                            fromEdgeId=fromEdge,
                            toEdgeId=toEdge,
                            fromLaneId=departLane,
                            toLaneId=arrivalLane)
            )

        df = pd.DataFrame(res)
        df.to_csv(join(args.output, "%s.csv" % node._id), index=False)

        print("####################################################################")
        print("[" + str(i) + " / " + str(args.to_index - args.from_index) + "]")


def go(scenario, args):
    traci.start([sumoBinary, "-c", scenario])

    end = int(3600 * (1 / args.step_length))

    try:
        for step in range(0, end):
            traci.simulationStep()
    except Exception as e:
        print(e)

    traci.close()
    sys.stdout.flush()


if __name__ == "__main__":

    args = create_args("Determine intersection volumes with SUMO")

    # read in intersections
    with open(args.input[0]) as f:
        selection = set(f.read().splitlines())

    net = sumolib.net.readNet(args.network, withConnections=True, withInternal=False, withFoes=False)
    allNodes = net.getNodes()  # all type of nodes

    # selecting only traffic light nodes
    traffic_light_nodes = []
    for node in allNodes:
        if node._type == "traffic_light" and node._type != "internal" and node._id in selection:
            traffic_light_nodes.append(node)

    print("Total number of traffic light junctions:", len(traffic_light_nodes))
    print("Processing: ", args.from_index, ' to ', args.to_index)

    run(args, traffic_light_nodes)
