
import os
from os.path import join
from glob import glob

import pandas as pd
import geopandas as gpd
import matsim
import simwrapper
import seaborn as sns
import matplotlib.pyplot as plt


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

base = read_trips("scenario-base", in_city_region)
base_nmc = read_trips("scenario-base--no-mc", in_city_region)

#%%%

sc_st = read_trips("scenario-st", in_city_region)
sc_mt = read_trips("scenario-mt", in_city_region)
sc_lt = read_trips("scenario-lt", in_city_region)

#%%

sc_st_nmc = read_trips("scenario-st--no-mc", in_city_region)
sc_mt_nmc = read_trips("scenario-mt--no-mc", in_city_region)
sc_lt_nmc = read_trips("scenario-lt--no-mc", in_city_region)

#%%

def norm(x):
    return round((x - 1) * 1000) / 10

def cmp(base, ref):
    b = base[base.main_mode == "car"]
    r = ref[ref.main_mode == "car"]
    
    print("trips", norm(len(r) / len(b)), "%")
    print("dist", norm(dist(r) / dist(b)), "%")
    print("tt",  norm(tt(r) / tt(b)), "%")


#%%

def netto_shift(series):
    # Calculate the netto mode-choice shift by substracting differences    
    for idx, count in series.iteritems():
        
        if idx[0] == idx[1]:
            continue
                
        r_idx = (idx[1], idx[0])
        
        reverse = df.loc[r_idx]
        
        if reverse < count:
            series.loc[r_idx] = 0
            series.loc[idx] = count - reverse
            
        else:            
            series.loc[r_idx] = reverse - count
            series.loc[idx] = 0        
        
        
    return series


shift_50 = pd.merge(base, acv50, on="trip_id")
df = shift_50.groupby(["main_mode_x", "main_mode_y"]).size()
df = netto_shift(df)

df.to_csv(join(out, "shift_acv50.csv"))

shift_100 = pd.merge(base, acv100, on="trip_id")
df = shift_100.groupby(["main_mode_x", "main_mode_y"]).size()
df = netto_shift(df)

df.to_csv(join(out, "shift_acv100.csv"))

#%%

c0 = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/base/congestion.csv.gz").set_index("link_id")
c100 = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/CV-50_ACV-50_AV-0--no-mc/congestion.csv.gz").set_index("link_id")
c50 = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/CV-0_ACV-100_AV-0--no-mc/congestion.csv.gz").set_index("link_id")

ci0 = city_links.merge(c0, left_on="link_id", right_index=True).drop(columns=['index_right', 'Quelle', 'Stand', 'congestion_index', 'average_daily_speed', 'wkt'])

# Only look at links that are at least slightly congested
ci0 = ci0[ci0.mean(axis=1) != 1]

#%%

# TODO: relative travel time für long-term (Mit MC)

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


#%%

# enum ("s" = straight, "t" = turn, "l" = left, "r" = right, "L" = partially left, R = partially right, "invalid" = no direction)

def direction(x):
    if x == "s":
        return "straight"
    elif x in ("L", "l"):
        return "left"    
    elif x in ("R", "r"):
        return "right"
    
    return "turn"

lanes = pd.read_csv("lanes.csv.gz").groupby(["fromEdgeId", "toEdgeId"]).first()
lanes["direction"] = lanes.dir.apply(direction)
lanes = lanes[["direction", "numLanes"]]


base = pd.read_csv("scenario-base.csv")

# Low flows are filtered out, because these are often network defects
base = base[base.flow > 350]

base = base.rename(columns={"flow": "base"})

#%%

df = pd.merge(base, lanes, left_on=["fromEdgeId", "toEdgeId"], right_index=True)

for sc in ("st", "mt", "lt"):
    
    tmp = pd.read_csv("scenario-%s.csv" % sc)
    tmp = tmp.drop(columns={"junctionId"})
        
    df = pd.merge(df, tmp, left_on=["fromEdgeId", "toEdgeId"], right_on=["fromEdgeId", "toEdgeId"])
    
    df[sc] = df.flow / df.base
    df = df.drop(columns=["flow"])
    
    
    
df = df.drop(columns={"junctionId", "fromEdgeId", "toEdgeId"})
    
df = pd.melt(df, id_vars=["direction"], value_vars=["st", "mt", "lt"], value_name="change",  var_name="scenario")

limits = {s: (float(df[df.scenario==s].quantile(0.02)), float(df[df.scenario==s].quantile(0.98))) for s in set(df.scenario)}

# Remove few outliers
def outlier(x):
    l, h = limits[x.scenario]    
    return l <= x.change <= h

df = df[df.apply(outlier, axis=1)]

df.to_csv(join(out, "flow", "intersections.csv"), index=False)

#%%

sns.set_theme(style="whitegrid", context="paper")

df.loc[df.direction != "straight", "direction"] = "turn"

df.loc[df.scenario == "st", "scenario"] = "Near future"
df.loc[df.scenario == "mt", "scenario"] = "Mid-term"
df.loc[df.scenario == "lt", "scenario"] = "Distant future"


fig, ax = plt.subplots(dpi=350, figsize=(9, 3.2))

ax = sns.violinplot(y="scenario", x="change", hue="direction", data=df, split=True, ax=ax)

plt.xlim(0.8, None)
plt.xlabel("Relative flow capacity")
plt.ylabel("Scenario")
