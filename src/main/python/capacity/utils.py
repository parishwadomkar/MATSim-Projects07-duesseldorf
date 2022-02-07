#!/usr/bin/env python

import argparse
import os
import sys
from subprocess import call


def create_args(description):
    import sumolib

    parser = argparse.ArgumentParser(description=description)

    parser.add_argument("input", nargs=1, help="Path to input csv")

    parser.add_argument("--output", default="output", help="Path to output folder")
    parser.add_argument("--network", type=str, default="../../../../scenarios/input/sumo.net.xml", help="Path to network file")
    parser.add_argument("--veh", type=int, default=5000, help="Vehicles per hour per lane simulate")
    parser.add_argument("--cv", type=float, default=1, help="Share of conventional vehicles")
    parser.add_argument("--av", type=float, default=0, help="Share of automated vehicles")
    parser.add_argument("--acv", type=float, default=0, help="Share of connected autonomous vehicles")
    parser.add_argument("--from-index", type=int, default=0, help="Start from number")
    parser.add_argument("--to-index", type=int, default=-1, help="Stop at number")
    parser.add_argument("--step-length", type=float, default=0.2, help="SUMO step length")
    parser.add_argument("--runner", type=str, default="runner0", help="Runner name")
    parser.add_argument("--runner-total", type=int, default=0, help="Total number of runners")
    parser.add_argument("--runner-index", type=int, default=0, help="Runner index")

    args = parser.parse_args()
    args.port = sumolib.miscutils.getFreeSocketPort()

    os.makedirs(args.output, exist_ok=True)
    os.makedirs(args.runner, exist_ok=True)

    return args


def init_workload(args, items):
    """ Set indices for the runner automatically """
    if args.runner_total <= 1:
        return

    n = len(items)
    step = n // args.runner_total

    args.from_index = args.runner_index * step
    args.to_index = min((args.runner_index + 1) * step, n)


def init_env():
    if 'SUMO_HOME' in os.environ:
        tools = os.path.join(os.environ['SUMO_HOME'], 'tools')
        sys.path.append(tools)
    else:
        sys.exit("please declare environment variable 'SUMO_HOME'")


def write_scenario(f, network_file, route_file, additional_file, step_length=0.2, time=600):
    """ Write sumo scenario file """

    with open(f, "w") as fn:
        fn.write("""<?xml version="1.0" encoding="UTF-8"?>
<configuration xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://sumo.dlr.de/xsd/sumoConfiguration.xsd">

    <input>
        <net-file value="%s"/>
        <route-files value="%s"/>
        <additional-files value="%s"/>
    </input>

    <time>
        <begin value="0"/>
        <end value="%d"/>
        <step-length value="%f"/>
    </time>
</configuration>
""" % (network_file, route_file, additional_file, time, step_length))


def filter_network(netconvert, netfile, edge, output, args=None):
    if isinstance(edge, list):
        x = [s[0] for e in edge for s in e.getShape()]
        y = [s[1] for e in edge for s in e.getShape()]
    else:
        x = [s[0] for s in edge.getShape()]
        y = [s[1] for s in edge.getShape()]

    # minX,minY,maxX,maxY
    boundary = ",".join(str(s) for s in [min(x) - 50, min(y) - 50, max(x) + 50, max(y) + 50])

    cmd = [netconvert, '-s', netfile, "--keep-edges.in-boundary", boundary]

    if args:
        cmd += args

    cmd += ['-o', output]

    call(cmd)