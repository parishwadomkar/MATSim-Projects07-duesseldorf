#!/usr/bin/env python

import os
import sys
import json

from subprocess import call


def init_env():
    if 'SUMO_HOME' in os.environ:
        tools = os.path.join(os.environ['SUMO_HOME'], 'tools')
        sys.path.append(tools)
    else:
        sys.exit("please declare environment variable 'SUMO_HOME'")


def write_scenario(f, network_file, route_file, additional_file, step_length=0.2):
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
        <end value="600"/>
        <step-length value="%f"/>
    </time>
</configuration>
""" % (network_file, route_file, additional_file, step_length))


def filter_network(netconvert, netfile, edge, output):

    x = [s[0] for s in edge.getShape()]
    y = [s[1] for s in edge.getShape()]

    # minX,minY,maxX,maxY
    boundary = ",".join(str(s) for s in [min(x) - 50, min(y) - 50, max(x) + 50, max(y) + 50])

    call([netconvert, '-s', netfile, "--keep-edges.in-boundary", boundary, '-o', output])

def saveToFile(data, filename):
    json.dump(data, open(filename, "w"))


def readFromFile(filename):
    if os.path.exists(filename):
        pass
    else:
        saveToFile([], filename)
    return (json.load(open(filename)))


def writeDone(data):
    temp = readFromFile("done.json")
    temp.append(data)
    saveToFile(temp, "done.json")