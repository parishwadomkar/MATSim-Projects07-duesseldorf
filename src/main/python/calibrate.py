#!/usr/bin/env python
# -*- coding: utf-8 -*-

import geopandas as gpd
import os
import pandas as pd

try:
    from matsim import calibration
except:
    import calibration

# %%

if os.path.exists("srv_raw.csv"):
    srv = pd.read_csv("srv_raw.csv")
    sim = pd.read_csv("sim.csv")

    _, adj = calibration.calc_adjusted_mode_share(sim, srv)

    print(srv.groupby("mode").sum())

    print("Adjusted")
    print(adj.groupby("mode").sum())

    adj.to_csv("srv_adj.csv", index=False)

# %%

modes = ["walk", "car", "pt", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -1.9,
    "pt": -0.7,
    "car": -1.4
}

# Use adjusted modal split for our distance distribution
target = {
    "bike": 0.179985,
    "car": 0.374772,
    "pt": 0.198782,
    "walk": 0.246461
}

region = gpd.read_file("../scenarios/input/shapeFile/duesseldorf-area.shp").set_crs("EPSG:25832")


def f(persons):
    df = gpd.sjoin(persons.set_crs("EPSG:25832"), region, how="inner", op="intersects")
    return df


def mt(df):

    # Car and ride are merged in the survey data
    df.loc[df.main_mode == "ride", "main_mode"] = "car"

    return df[df.main_mode != "freight"]


# print(calibration.calc_mode_share("runs/015", map_trips=filter_freight))

study, obj = calibration.create_mode_share_study("calib", "matsim-duesseldorf-1.5-SNAPSHOT.jar",
                                                 "../scenarios/input/duesseldorf-v1.0-1pct.config.xml",
                                                 modes, target,
                                                 initial_asc=initial,
                                                 args="--25pct --no-lanes --dc 1.14",
                                                 jvm_args="-Xmx46G -Xmx46G -XX:+AlwaysPreTouch",
                                                 person_filter=f, map_trips=mt)

# %%

study.optimize(obj, 10)
