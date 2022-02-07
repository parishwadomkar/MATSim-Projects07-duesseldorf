
import os
from os.path import join

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
json = gdf[["wkt"]].to_json()

with open(join(out, "network.geojson"), "w") as f:
    f.write(json)

#%%

df = pd.read_csv("Z:/matsim-duesseldorf/experiment/output/base/linkStats.csv.gz")

#%%

def f(col):
    return ";".join(str(x) for x in col)

link_volumes = df.groupby("linkId").agg(vol=("vol_car", f))
link_volumes = gdf.merge(link_volumes, left_on="link_id", right_index=True)

with open(join(out, "link_volumes.csv"), "w") as f:
    
    f.write("link;")    
    f.write(";".join("%02d:00" % h for h in range(24)))
    f.write("\n")
    
    for row in link_volumes.itertuples():
        
        f.write(row.Index)
        f.write(";")
        f.write(row.vol)
        f.write("\n")
        


#%%



