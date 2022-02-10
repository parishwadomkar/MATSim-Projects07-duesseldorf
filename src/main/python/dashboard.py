
import os
from os.path import join
from glob import glob

import pandas as pd
import geopandas as gpd
import matsim
import simwrapper

out = "../simwrapper"

#%%

network = matsim.read_network("../../../scenarios/input/duesseldorf-v1.7-network.xml.gz")

#%%

shp = gpd.read_file("../../../../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/input/area/area.shp")

city = gpd.read_file("../../../../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp").set_crs("EPSG:25832")


#%%

link_coords = []

nodes = network.nodes.set_index("node_id")

for link in network.links.itertuples():
    
    f = nodes.loc[link.from_node]
    t = nodes.loc[link.to_node]
    
    link_coords.append({
        "link_id" : link.link_id,
        "wkt": f"LINESTRING ({f.x} {f.y}, {t.x} {t.y})"
    })    
 

df = pd.DataFrame(link_coords)

df["wkt"] = gpd.GeoSeries.from_wkt(df["wkt"], crs="EPSG:25832")

#%%

gdf = gpd.GeoDataFrame(df, geometry="wkt").set_index("link_id")

city_links = gpd.sjoin(gdf, city, how="inner", op="intersects")

gdf = gpd.sjoin(gdf, shp, how="inner", op="intersects").to_crs("EPSG:4326")


#%%

# Only use relevant columns
json = gdf[["wkt"]].reset_index().to_json()

with open(join(out, "network.geojson"), "w") as f:
    f.write(json)

#%%

df = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/base/linkStats.csv.gz")

#%%


link_volumes = df.groupby("linkId").agg(volume=("vol_car", sum))
link_volumes = gdf.merge(link_volumes, left_on="link_id", right_index=True)

with open(join(out, "link_volumes.csv"), "w") as f:
    
    f.write("link;volume\n")
    for row in link_volumes.itertuples():
        
        f.write(row.Index)
        f.write(";")
        f.write(str(row.volume))
        f.write("\n")
        
#%%

iters = [d for d in os.scandir("Z:/matsim-duesseldorf/experiment/output/base/ITERS/") if d.is_dir()]
iters = sorted(iters, key=lambda d: d.stat().st_atime)

hist = glob(iters[-1].path + "/*.legHistogram.txt")[0]

df = pd.read_csv(hist, delimiter="\t", index_col=0)

df = df.rename(columns={"en-route_car": "Car"})[["Car"]]

# Only use 24 hours
df.iloc[0:(24*12)].to_csv(join(out, "base_car_traffic.csv"))


#%%

from matsim.calibration import calc_mode_share

def pf(persons):
    df = gpd.sjoin(persons.set_crs("EPSG:25832"), city, how="inner", op="intersects")
    return df

def mt(df):

    # Car and ride are merged in the survey data
    df.loc[df.main_mode == "ride", "main_mode"] = "car"
    return df[df.main_mode != "freight"]

#mode_share = calc_mode_share("Z:/matsim-duesseldorf/experiment/output/base", person_filter=pf, map_trips=mt)

#%%

with open(join(out, "base_modeshare.csv"), "w") as f:

    f.write(",".join(mode_share.index))
    f.write("\n")    
    f.write(",".join(str(s) for s in mode_share))


#%%
from subprocess import call, check_output
from os.path import abspath

def cp(scenario, f, dst):  
    if os.path.exists(dst):
        os.remove(dst)

    cmd = ["scp", "rakow@cluster.math.tu-berlin.de:/net/ils/matsim-duesseldorf/experiment/output/%s/%s" % (scenario, f), dst]
    out = check_output(cmd)
    return dst


def read_trips(run, filter_trips=None):
    """ Read trips and persons from run directory """
    trips = cp(run, "dd.output_trips.csv.gz", "trips_tmp.csv.gz")

    df = pd.read_csv(trips, sep=";")
    nans = df.main_mode.isnull()

    # use longest distance mode if there is no main mode
    df.loc[nans, "main_mode"] = df.loc[nans, "longest_distance_mode"]

    if filter_trips is not None:
        df = df[df.apply(filter_trips, axis=1)]

    df = mt(df)

    return df

#%%

from shapely.geometry import Point

geom = city.loc[0].geometry.envelope

def in_city_region(r):
    
    if r.main_mode == "freight":
        return False
    
    #if r.main_mode != "car":
    #    return False
    
    f = Point(r.start_x, r.start_y)
    t = Point(r.end_x, r.end_y)
    
    if geom.contains(f) and geom.contains(t):
        return True
    
    return False

def tt(df):
    return sum(pd.to_timedelta(df.trav_time).dt.seconds)

def dist(df):
    return sum(df.traveled_distance)

#%%

base = read_trips("base", in_city_region)

#%%%

acv100 = read_trips("CV-0_ACV-100_AV-0", in_city_region)
acv25 = read_trips("CV-75_ACV-25_AV-0", in_city_region)
acv50 = read_trips("CV-50_ACV-50_AV-0", in_city_region)
acv75 = read_trips("CV-25_ACV-75_AV-0", in_city_region)

#%%

acv50_st = read_trips("CV-50_ACV-50_AV-0--no-mc", in_city_region)
acv100_st = read_trips("CV-0_ACV-100_AV-0--no-mc", in_city_region)

#%%

shift_50 = pd.merge(base, acv50, on="trip_id")
df = shift_50.groupby(["main_mode_x", "main_mode_y"]).size()
df.to_csv(join(out, "shift_acv50.csv"))

shift_100 = pd.merge(base, acv100, on="trip_id")
df = shift_100.groupby(["main_mode_x", "main_mode_y"]).size()
df.to_csv(join(out, "shift_acv100.csv"))

#%%

c0 = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/base/congestion.csv.gz").set_index("link_id")
c100 = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/CV-50_ACV-50_AV-0--no-mc/congestion.csv.gz").set_index("link_id")
c50 = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/CV-0_ACV-100_AV-0--no-mc/congestion.csv.gz").set_index("link_id")

ci0 = city_links.merge(c0, left_on="link_id", right_index=True).drop(columns=['index_right', 'Quelle', 'Stand', 'congestion_index', 'average_daily_speed', 'wkt'])

# Only look at links that are at least slightly congested
ci0 = ci0[ci0.mean(axis=1) != 1]

#%%

def convertSeconds(seconds):
    h = seconds//(60*60)
    m = (seconds-h*60*60)//60
    s = seconds-(h*60*60)-(m*60)
    return [h, m, s]

def ci_index(df, name):
    ds = []
    for t, y in df.mean().iteritems():
    
        #t = convertSeconds(int(t))    
    
        ds.append({
            "scenario": name,
            "t": int(t),
            "y": y
        })
    return ds


ci100 = c100.loc[ci0.index].drop(columns=['congestion_index', 'average_daily_speed'])
ci50 = c50.loc[ci0.index].drop(columns=['congestion_index', 'average_daily_speed'])


#%%

ds = []

ds.extend(ci_index(ci0, "Base"))
ds.extend(ci_index(ci50, "50% ACV"))
ds.extend(ci_index(ci100, "100% ACV"))

df = pd.DataFrame(ds)

df.to_csv(join(out, "rel_tt.csv"), index=False)

