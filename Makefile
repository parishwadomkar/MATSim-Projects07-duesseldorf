
JAR := matsim-duesseldorf-*.jar
V := v1.0

export SUMO_HOME := $(abspath ../../sumo-1.6.0/)
osmosis := osmosis\bin\osmosis

.PHONY: prepare

$(JAR):
	mvn package

# Required files
scenarios/input/network.osm.pbf:
	curl https://download.geofabrik.de/europe/germany/nordrhein-westfalen-201209.osm.pbf\
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
	 --used-node --wx $@

	#$(osmosis) --rb file=$<\
	# --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
	# --bounding-box top=51.46 left=6.60 bottom=50.98 right=7.03\
	# --used-node --wb network-coarse.osm.pbf

	#$(osmosis) --rb file=network-detailed.osm.pbf\
  	# --merge --wx $@

	#rm network-detailed.osm.pbf
	#rm network-coarse.osm.pbf


scenarios/input/sumo.net.xml: scenarios/input/network.osm

	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess\
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
	 --output $@

scenarios/input/duesseldorf-$V-network-with-pt.xml.gz: scenarios/input/duesseldorf-$V-network.xml.gz scenarios/input/gtfs-vrs.zip scenarios/input/gtfs-vrr.zip scenarios/input/gtfs-avv.zip
	java -jar $(JAR) prepare transit --network $< $(filter-out $<,$^)

scenarios/input/duesseldorf-$V-25pct.plans.xml.gz:
	java -jar $(JAR) prepare population\
	 --population ../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/optimizedPopulation_filtered.xml.gz\
	 --attributes  ../../shared-svn/komodnext/matsim-input-files/duesseldorf-senozon/personAttributes.xml.gz


# Aggregated target
prepare: scenarios/input/duesseldorf-$V-25pct.plans.xml.gz scenarios/input/duesseldorf-$V-network-with-pt.xml.gz
	echo "Done"