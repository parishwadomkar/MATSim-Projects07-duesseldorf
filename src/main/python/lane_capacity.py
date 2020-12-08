#!/usr/bin/env python
# -*- coding: utf-8 -*-

import pandas as pd
from bs4 import BeautifulSoup

#%%

def read_network(sumo_network):
    """ Read sumo network from xml file. """

    with open(sumo_network) as f:
        network = BeautifulSoup(f, "lxml-xml")
        
    edges = {}
    junctions = {}
    
    for edge in network.findAll("edge"):
        edges[edge["id"]] = edge
    
    for j in network.findAll("junction"):
        junctions[j["id"]] = j
    
    
    data = []

    for conn in network.findAll("connection"):
    
        fromEdge = edges[conn["from"]]
        fromLane = fromEdge.find("lane", {"index": conn["fromLane"]})
        
        toEdge = edges[conn["to"]]
        toLane = toEdge.find("lane", {"index": conn["toLane"]})
        
        junction = junctions[fromEdge["to"]]
            
        d = {
            "junction": junction["id"],
            "fromEdgeId": fromEdge["id"],
            "toEdgeId": toEdge["id"],
            "fromLaneId":fromLane["id"],
            "toLaneId": toLane["id"],
            "dir": conn["dir"],
            "state": conn["state"],
            "edgeType": fromEdge["type"],
            "numLanes": len(fromEdge.findAll("lane")),
            "priority": int(fromEdge["priority"]),
            "speed": float(fromLane["speed"]),
            "junctionType": junction["type"],
            "junctionSize": len(junction.findAll("request"))
        }
        
        # TODO: request and foes
        
        data.append(d)
        
    return pd.DataFrame(data)

#%%

network = read_network("../../../scenarios/input/sumo.net.xml")

#%%

network.to_csv("lanes.csv.gz")
