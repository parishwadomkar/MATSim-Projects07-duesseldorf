#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os

import pandas as pd
import lxml.etree as ET
from shapely.geometry import LineString

#%%

os.chdir(os.path.dirname(os.path.abspath(__file__)))


#%%

def parse_ls(el):
    shape = el.attrib['shape']
    coords = [tuple(map(float, l.split(","))) for l in shape.split(" ")]
    return LineString(coords)

def read_network(sumo_network):
    """ Read sumo network from xml file. """
        
    edges = {}
    junctions = {}
    # count the indices of connections, assuming they are ordered
    # this seems to be the case according to sumo doc. there is no further index attribute
    idx = {}
    data = []
    
    for _, elem in ET.iterparse(sumo_network, events=("end",),
                                tag=('edge', 'junction', 'connection'),
                                remove_blank_text=True):
        
        if elem.tag == "edge":
            edges[elem.attrib["id"]] = elem
            continue
        elif elem.tag == "junction":
            junctions[elem.attrib["id"]] = elem
            idx[elem.attrib["id"]] = 0
            continue
        
        if elem.tag != "connection":
            continue
    
        # Rest is parsing connection        
        conn = elem.attrib
    
        fromEdge = edges[conn["from"]]
        fromLane = fromEdge.find("lane", {"index": conn["fromLane"]})
        
        toEdge = edges[conn["to"]]
        toLane = toEdge.find("lane", {"index": conn["toLane"]})
        
        junction = junctions[fromEdge.attrib["to"]]

        request = junction.find("request", {"index": str(idx[fromEdge.attrib["to"]])})

        # increase request index
        idx[fromEdge.attrib["to"]] += 1
            
        d = {
            "junctionId": junction.attrib["id"],
            "fromEdgeId": fromEdge.attrib["id"],
            "toEdgeId": toEdge.attrib["id"],
            "fromLaneId": fromLane.attrib["id"],
            "toLaneId": toLane.attrib["id"],
            "dir": conn["dir"],
            "state": conn["state"],
            "edgeType": fromEdge.attrib["type"],
            "fromLength": float(fromLane.attrib["length"]),
            "numLanes": len(fromEdge.findall("lane")),
            "numResponse": request.attrib["response"].count("1"),
            "numFoes": request.attrib["foes"].count("1"),
            "connDistance": parse_ls(fromLane).distance(parse_ls(toLane)),
            "priority": int(fromEdge.attrib["priority"]),
            "speed": float(fromLane.attrib["speed"]),
            "junctionType": junction.attrib["type"],
            "junctionSize": len(junction.findall("request"))
        }

        data.append(d)
        
    return pd.DataFrame(data)

def read_edges(sumo_network):
    data = []
    
    edges = {}
    junctions = {}

    for _, elem in ET.iterparse(sumo_network, events=("end",),
                                tag=('edge', 'junction'),
                                remove_blank_text=True):

        if elem.tag == "edge":
            edges[elem.attrib["id"]] = elem
            continue
        elif elem.tag == "junction":
            junctions[elem.attrib["id"]] = elem
            continue


    for k,v in edges.items():
        
        lane = v.find("lane")
        
        f = junctions[v.attrib["from"]]
        t = junctions[v.attrib["to"]]

        data.append({
            "edgeId": k,
            "name": v.attrib.get("name", ""),
            "from": v.attrib["from"],
            "to": v.attrib["to"],
            "type": v.attrib["type"],
            "speed": lane.attrib["speed"],
            "length": float(lane.attrib["length"]),
            "numLanes": len(v.findall("lane")),
            "fromType": f.attrib["type"],
            "toType": t.attrib["type"]
        })

    return pd.DataFrame(data)

#%%

if __name__ == "__main__":

    network = read_network("../../../scenarios/input/sumo.net.xml")
    #edges = read_edges("../../../scenarios/input/sumo.net.xml")

    network.to_csv("lanes.csv.gz", index=False)


#%%

res = []

for k, _ in edges.speed.value_counts()[:-4].items():
    
    df = edges[ (edges.speed == k) & (edges.fromType == "priority") & (edges.toType == "priority") & (edges.length > 80) & (edges.length < 400)]
    
    for s, v2 in df.numLanes.value_counts().items():
    
        if v2 < 5: continue
    
        sf = df[df.numLanes == s]
        
        res.append(sf.sample(5))
    

#%%

pd.concat(res).to_csv("sample.csv", index=False)

