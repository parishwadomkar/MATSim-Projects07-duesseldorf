#!/usr/bin/env python
# @author  Angelo Banse, Ronald Nippold, Christian Rakow

import argparse
import os
import sys
from os.path import join, basename

from utils import init_env, write_scenario, filter_network

init_env()

import sumolib.net
import traci  # noqa
from sumolib import checkBinary  # noqa
import lxml.etree as ET

import pandas as pd
import numpy as np

sumoBinary = checkBinary('sumo')
netconvert = checkBinary('netconvert')


def capacity_estimate(v):
    tT = 1.2
    lL = 7.0
    Qc = v / (v * tT + lL)

    return 3600 * Qc


def writeRouteFile(f_name, departLane, arrivalLane, edges, veh, qCV, qAV, qACV):
    text = """<?xml version="1.0" encoding="UTF-8"?>
<routes xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/routes_file.xsd">

    <vTypeDistribution id="vDist">
        <vType id="vehCV" probability="{qCV}" color="1,0,0" vClass="passenger"/>
        <vType id="vehACV" probability="{qACV}" color="0,0,1" vClass="passenger" minGap="0.5" accel="2.6" decel="3.5" sigma="0" tau="0.6" speedFactor="1" speedDev="0"/>
        <vType id="vehAV" probability="{qAV}" color="0,1,0" vClass="passenger" decel="3.0" sigma="0.1" tau="1.5" speedFactor="1" speedDev="0"/>
    </vTypeDistribution>

    <flow id="veh" begin="0" end= "600" vehsPerHour="{veh}" type="vDist" departLane="{departLane}" arrivalLane="{arrivalLane}" departSpeed="max">
        <route edges="{edges}"/>
    </flow>

</routes>
"""
    # departSpeed="speedLimit" ?
    context = {
        "departLane": departLane,
        "arrivalLane": arrivalLane,
        "edges": edges,
        "veh": veh,
        "qCV": qCV,
        "qAV": qAV,
        "qACV": qACV
    }

    with open(f_name, "w") as f:
        f.write(text.format(**context))


def writeDetectorFile(f_name, output, detectorName, lane, laneNr, output_file):
    text = """<?xml version="1.0" encoding="UTF-8"?>

	<additional xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/additional_file.xsd">
	        %s
	</additional>

	""" % "\n".join(
        """<e1Detector id="{detectorName}_%d" lane="{lane}_%d" pos="-15" friendlyPos="true" freq="10.00" file="{output_file}_%d.xml"/>""" % (i, i, i)
        for i in
        range(laneNr))

    context = {
        "detectorName": detectorName,
        "lane": lane,
        "laneNr": laneNr,
        "output_file": join("..", output, output_file)
    }

    with open(f_name, 'w') as f:
        f.write(text.format(**context))


def read_result(folder, edge, scale):
    data = []

    for f in os.listdir(folder):
        if not f.startswith(edge) or not f.endswith(".xml"):
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

        data.append({
            "laneId": f.replace(".xml", ""),
            "flow": total * (3660 / (end - 60)),
            "scale": scale,
            "count": total
        })

    return data


def run(args, edges):
    # saveToFile(edges_ids,"junctions.json")
    i = 0

    os.makedirs(args.output, exist_ok=True)
    os.makedirs(args.runner, exist_ok=True)

    total = args.cv + args.av + args.acv

    qCV = (args.cv / total)
    qAV = (args.av / total)
    qACV = (args.acv / total)

    print("Running vehicle shares cv: %.2f, av: %.2f, acv: %.2f" % (qCV, qAV, qACV))

    if args.to_node <= 0:
        args.to_node = len(edges)

    #    for edge in list:		### total: 1636
    for x in range(args.from_node, args.to_node):
        edge = edges[x]
        i += 1
        print("Edge id: ", edge._id)
        print("Number of lanes: ", edge.getLaneNumber(), "speed:", edge.getSpeed())
        file_name = (str(edge._id))  # use as detector output name
        detector_name = (str(edge._id))  # use as detector name
        laneNr = edge.getLaneNumber()  # nr of lanes

        cap = capacity_estimate(edge.getSpeed()) * 0.9 * laneNr

        print("Capacity estimate:", cap)

        p_network = join(args.runner, "filtered.net.xml")
        p_routes = join(args.runner, "route.rou.xml")
        p_detector = join(args.runner, "detector.add.xml")

        filter_network(netconvert, args.network, edge, p_network)
        writeRouteFile(p_routes, "best", "current", edge._id, cap, qCV, qAV, qACV)
        writeDetectorFile(p_detector, args.output, detector_name, edge._id, laneNr, file_name)

        p_scenario = join(args.runner, "scenario.sumocfg")

        write_scenario(p_scenario, basename(p_network), basename(p_routes), basename(p_detector), args.step_length)

        go(p_scenario, p_network, edge._id, args)
        print("####################################################################")
        print("[" + str(i) + " / " + str(args.to_node - args.from_node) + "]")


def go(scenario, network, edge, args):
    # while traci.simulation.getMinExpectedNumber() > 0:

    end = int(600 * (1 / args.step_length))

    res = []

    # Simulate different scales
    for scale in np.arange(1, 2.1, 0.05):

        print("Running scale", scale)

        traci.start([sumoBinary, "-c", scenario, "--scale", str(scale)])
        try:
            for step in range(0, end):
                traci.simulationStep()
        except Exception as e:
            print(e)

        traci.close()

        res.extend(read_result(args.output, edge, scale))

    df = pd.DataFrame(res)
    df.to_csv(join(args.output, "%s.csv" % edge), index=False)

    sys.stdout.flush()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Determine edge volumes with SUMO")

    parser.add_argument("edges", nargs=1, help="Path to edge csv")

    parser.add_argument("--output", default="output", help="Path to output folder")
    parser.add_argument("--network", type=str, default="../../../../scenarios/input/sumo.net.xml", help="Path to network file")
    parser.add_argument("--veh", type=int, default=5000, help="Vehicles per hour per lane simulate")
    parser.add_argument("--cv", type=float, default=1, help="Share of conventional vehicles")
    parser.add_argument("--av", type=float, default=0, help="Share of automated vehicles")
    parser.add_argument("--acv", type=float, default=0, help="Share of connected autonomous vehicles")
    parser.add_argument("--from-node", type=int, default=0, help="Start from edge number")
    parser.add_argument("--to-node", type=int, default=-1, help="Stop at edge number")
    parser.add_argument("--step-length", type=float, default=0.2, help="SUMO step length")
    parser.add_argument("--runner", type=str, default="runner0", help="Runner id")

    args = parser.parse_args()

    net = sumolib.net.readNet(args.network, withConnections=False, withInternal=False, withFoes=False)

    allEdges = net.getEdges()  # all type of edges

    selection = set(pd.read_csv(args.edges[0]).edgeId)

    # select if edges in net file
    edges = [edge for edge in allEdges if edge._id in selection]

    # storing edge id's in list
    edge_ids = [edge._id for edge in edges]

    print("Total number of edges:", len(edge_ids))
    print("Processing: ", args.from_node, ' to ', args.to_node)

    run(args, edges)
