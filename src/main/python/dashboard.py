
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

mode_share = calc_mode_share("Z:/matsim-duesseldorf/experiment/output/base", person_filter=pf, map_trips=mt)

#%%

with open(join(out, "base_modeshare.csv"), "w") as f:

    f.write(",".join(mode_share.index))
    f.write("\n")    
    f.write(",".join(str(s) for s in mode_share))



