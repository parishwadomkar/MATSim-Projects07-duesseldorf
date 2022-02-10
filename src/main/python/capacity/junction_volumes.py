#!/usr/bin/env python
# @author  Angelo Banse, Ronald Nippold, Christian Rakow

import os
import shutil
import sys
from os.path import join, basename

from utils import init_env, create_args, init_workload, write_scenario, filter_network, vehicle_parameter

init_env()

import traci  # noqa
import sumolib.net
from sumolib import checkBinary  # noqa
import lxml.etree as ET

import pandas as pd

sumoBinary = checkBinary('sumo')
netconvert = checkBinary('netconvert')


def writeRouteFile(f_name, routes, extra_routes, qCV, qAV, qACV, scenario=None):
    text = """<?xml version="1.0" encoding="UTF-8"?>

<routes xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/routes_file.xsd">


"""
    if scenario is None:
        text += """
        <vTypeDistribution id="vDist">
            <vType id="vehCV" probability="{qCV}" color="1,0,0" vClass="passenger" impatience="0.2"/>
            <vType id="vehACV" probability="{qACV}" color="0,0,1" vClass="passenger" minGap="0.5" accel="2.6" decel="3.5" sigma="0" tau="0.6" speedFactor="1" speedDev="0" impatience="0"/>
            <vType id="vehAV" probability="{qAV}" color="0,1,0" vClass="passenger" decel="3.0" sigma="0.1" tau="1.2" speedFactor="1" speedDev="0" />
        </vTypeDistribution>
        """
    else:
        text += """<vTypeDistribution id="vDist">
                        %s
                    </vTypeDistribution>
                """ % vehicle_parameter(scenario)


    for i, edges in enumerate(routes):
        text += """
            <flow id="veh%d" begin="0" end= "1800" vehsPerHour="5000" type="vDist" departLane="best" arrivalLane="current" departSpeed="max">
               <route edges="%s"/>
            </flow>
        """ % (i, edges)

    for i, edges in enumerate(extra_routes):
        text += """
            <flow id="vehx%d" begin="0" end= "1800" vehsPerHour="500" type="vDist" departLane="best" arrivalLane="current" departSpeed="max">
               <route edges="%s"/>
            </flow>
        """ % (i, edges)

    text += "</routes>"

    context = {
        "qCV": qCV,
        "qAV": qAV,
        "qACV": qACV
    }
    with open(f_name, "w") as f:
        f.write(text.format(**context))


def writeDetectorFile(f_name, output, lanes):
    text = """<?xml version="1.0" encoding="UTF-8"?>
        <additional xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/additional_file.xsd">
"""

    for i, lane in enumerate(lanes):
        text += """
        <e1Detector id="detector%d" lane="%s" pos="-1" friendlyPos="true" freq="10.00" file="%s.xml"/>
    """ % (i, lane, join(output, "lane%d" % i))

    text += "</additional>"

    with open(f_name, 'w') as f:
        f.write(text)


def read_result(folder, **kwargs):
    flow = 0

    for f in os.listdir(folder):
        if not f.endswith(".xml"):
            continue

        total = 0
        end = 0

        for _, elem in ET.iterparse(join(folder, f), events=("end",),
                                    tag=('interval',),
                                    remove_blank_text=True):

            begin = float(elem.attrib["begin"])
            end = float(elem.attrib["end"])
            if begin < 60:
                continue

            total += float(elem.attrib["nVehContrib"])

        flow += total * (3600 / (end - 60))

    kwargs["flow"] = flow
    return kwargs


def run(args, nodes):
    total = args.cv + args.av + args.acv

    qCV = (args.cv / total)
    qAV = (args.av / total)
    qACV = (args.acv / total)

    if args.scenario:
        print("Running scenario: " + args.scenario)
    else:
        print("Running vehicle shares cv: %.2f, av: %.2f, acv: %.2f" % (qCV, qAV, qACV))

    if args.to_index <= 0:
        args.to_index = len(nodes)

    i = 0

    for x in range(args.from_index, args.to_index):
        node = nodes[x]
        i += 1

        print("####################################################################")
        print("Junction id: " + node._id)

        folder = join(args.runner, "detector")
        p_network = join(args.runner, "filtered.net.xml")

        edges = [c.getFrom() for c in node.getConnections()] + [c.getTo() for c in node.getConnections()]

        filter_network(netconvert, args.network, edges, p_network, ["--no-internal-links", "false"])

        pairs = set((c.getFrom(), c.getTo()) for c in node.getConnections() if c._direction != c.LINKDIR_TURN)

        res = []

        for fromEdge, toEdge in pairs:

            # Clean old data
            shutil.rmtree(folder, ignore_errors=True)
            os.makedirs(folder, exist_ok=True)

            p_scenario = join(args.runner, "scenario.sumocfg")
            p_routes = join(args.runner, "route.rou.xml")
            p_detector = join(args.runner, "detector.add.xml")

            routes = []

            # Build routes by trying to use incoming edge, when it is too short
            if fromEdge._length < 30:
                routes = [k._id + " " + fromEdge._id + " " + toEdge._id for k, v in fromEdge._incoming.items() if
                          all(d._direction not in (d.LINKDIR_TURN, d.LINKDIR_LEFT, d.LINKDIR_RIGHT) for d in v)]

            if not routes:
                routes = [fromEdge._id + " " + toEdge._id]

            extra_routes = []
            # Produce car traffic on the other connections
            for c in node.getConnections():
                if c._direction == c.LINKDIR_TURN:
                    continue

                if c.getFrom() == fromEdge or c.getTo() == toEdge:
                    continue

                r = c.getFrom()._id + " " + c.getTo()._id
                if r not in extra_routes:
                    extra_routes.append(r)

            lanes = [fromEdge._id + "_" + str(i) for i in range(len(fromEdge._lanes))]

            writeRouteFile(p_routes, routes, extra_routes, qCV, qAV, qACV, args.scenario)

            writeDetectorFile(p_detector, "detector", lanes)

            write_scenario(p_scenario, basename(p_network), basename(p_routes), basename(p_detector), args.step_length, time=1800)

            go(p_scenario, args)

            # Read output
            res.append(read_result(folder,
                                   junctionId=node._id,
                                   fromEdgeId=fromEdge._id,
                                   toEdgeId=toEdge._id))

        df = pd.DataFrame(res)
        df.to_csv(join(args.output, "%s.csv" % node._id), index=False)

        print("####################################################################")
        print("[" + str(i) + " / " + str(args.to_index - args.from_index) + "]")


def go(scenario, args):
    traci.start([sumoBinary, "-c", scenario])

    end = int(1800 * (1 / args.step_length))

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

    init_workload(args, traffic_light_nodes)

    print("Processing: ", args.from_index, ' to ', args.to_index)

    run(args, traffic_light_nodes)
