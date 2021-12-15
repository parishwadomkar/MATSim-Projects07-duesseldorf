
JAR := matsim-duesseldorf-*.jar
V := v1.6
CRS := EPSG:25832

export SUMO_HOME := $(abspath ../../sumo-1.8.0/)
osmosis := osmosis\bin\osmosis

.PHONY: prepare

$(JAR):
	mvn package

# Required files
scenarios/input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany-210101.osm.pbf\
	  -o scenarios/input/network.osm.pbf

scenarios/input/gtfs-vrr.zip:
	curl https://openvrr.de/dataset/c415abd6-3b63-4a1f-8a17-9b77cf5f09ec/resource/7d1b5433-92c3-4603-851e-728acbb52793/download/2020_06_04_google_transit_verbundweit_inlkl_spnv.zip\
	  -o $@

scenarios/input/gtfs-vrs.zip:
	curl https://download.vrsinfo.de/gtfs/GTFS_VRS_mit_SPNV_hID_GlobalID.zip\
	 -o $@

scenarios/input/gtfs-avv.zip:
	curl http://opendata.avv.de/current_GTFS/AVV_GTFS_Masten_mit_SPNV_Global-ID.zip\
	 -o $@

scenarios/input/network.osm: scenarios/input/network.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street\
	 --bounding-box top=51.65 left=6.00 bottom=50.60 right=7.56\
	 --used-node --wb network-detailed.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,motorway_junction\
	 --bounding-box top=52.04 left=6.00 bottom=50.02 right=8.62\
	 --used-node --wb network-coarse.osm.pbf

	$(osmosis) --rb file=network-coarse.osm.pbf --rb file=network-detailed.osm.pbf\
  	 --merge --wx $@

	rm network-detailed.osm.pbf
	rm network-coarse.osm.pbf

# One ramp has been excluded due to errors
scenarios/input/sumo.net.xml: scenarios/input/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --ramps.no-split\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml,$(SUMO_HOME)/data/typemap/osmNetconvertUrbanDe.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger --remove-edges.by-type highway.track,highway.services,highway.unsurfaced\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --proj "+proj=utm +zone=32 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
	 --osm-files $< -o=$@

scenarios/input/duesseldorf-$V-network.xml.gz: scenarios/input/sumo.net.xml
	java -jar $(JAR) prepare network $< scenarios/input/herzogstrasse.net.xml\
	 --capacities CV-100_AV-000.csv\
	 --output $@

scenarios/input/duesseldorf-$V-network-with-pt.xml.gz: scenarios/input/duesseldorf-$V-network.xml.gz scenarios/input/gtfs-vrs.zip scenarios/input/gtfs-vrr.zip scenarios/input/gtfs-avv.zip
	java -jar $(JAR) prepare transit-from-gtfs $(filter-out $<,$^)\
	 --network $<\
	 --name "duesseldorf-$V"\
	 --target-crs $(CRS)\
	 --date "2020-06-08"\
	 --include-stops "org.matsim.prepare.FilterTransitStops"

scenarios/input/freight-trips.xml.gz:
	java -jar $(JAR) prepare extract-freight-trips ../shared-svn/projects/german-wide-freight/v1.1/german-wide-freight-25pct.xml.gz\
	 --network ../shared-svn/projects/german-wide-freight/original-data/german-primary-road.network.xml.gz\
	 --input-crs EPSG:5677\
	 --target-crs $(CRS)\
	 --shp ../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/input/shapeFiles/freight/newDusseldorfBoundary.shp\
	 --cut-on-boundary\
	 --output $@

scenarios/input/duesseldorf-$V-10pct.plans.xml.gz: scenarios/input/freight-trips.xml.gz

	java -jar $(JAR) prepare trajectory-to-plans\
	 --name prepare\
	 --sample-size 0.25\
	 --population ../../shared-svn/komodnext/matsim-input-files/20210216_duesseldorf_2/optimizedPopulation_filtered.xml.gz\
	 --attributes ../../shared-svn/komodnext/matsim-input-files/20210216_duesseldorf_2/personAttributes.xml.gz\

	# Landuse data must be copied to the specified location
	java -jar $(JAR) prepare resolve-grid-coords\
	 scenarios/input/prepare-25pct.plans.xml.gz\
	 --grid-resolution 500\
	 --input-crs $(CRS)\
	 --landuse scenarios/input/landuse/landuse.shp\
	 --output scenarios/input/prepare-25pct.plans.xml.gz

	java -jar $(JAR) prepare generate-short-distance-trips\
	 --population scenarios/input/prepare-25pct.plans.xml.gz\
	 --shp ../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp\
	 --input-crs EPSG:25832\
	 --num-trips 95000

	java -jar $(JAR) prepare merge-populations scenarios/input/prepare-25pct.plans-with-trips.xml.gz $<\
	 --output scenarios/input/duesseldorf-$V-25pct.plans.xml.gz

	java -jar $(JAR) prepare downsample-population scenarios/input/duesseldorf-$V-25pct.plans.xml.gz\
	 --sample-size 0.25\
	 --samples 0.1 0.01

	java -jar $(JAR) prepare extract-home-coordinates scenarios/input/duesseldorf-$V-25pct.plans.xml.gz\
	 --csv scenarios/input/duesseldorf-$V-homes.csv


check:
	java -jar $(JAR) analysis check-population scenarios/input/duesseldorf-$V-25pct.plans.xml.gz\
	 --shp ../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp\
	 --input-crs $(CRS)\

# Aggregated target
prepare: scenarios/input/duesseldorf-$V-10pct.plans.xml.gz scenarios/input/duesseldorf-$V-network-with-pt.xml.gz
	echo "Done"