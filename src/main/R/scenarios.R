

library(tmap)
library(tmaptools)
library(lubridate)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")


source("theme.R")
source("utils.R")

theme_set(theme_Publication(18))

#network <- read_network("../../../scenarios/input/duesseldorf-v1.7-network.xml.gz")

geom <- read_link_geom("../../../scenarios/input/duesseldorf-v1.7-network-linkGeometries.csv", 25832, simplify = 0)

#f <- "\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\experiment\\output\\scenario-base--no-mc"
f <- "../../../runs/baseline-nomc/"

read_scenario <- function(scenario) {
  read_link_stats(paste("../../../runs/", scenario, sep="")) %>%
    group_by(linkId) %>%
    summarise(vol=sum(vol_car), tt=mean(avgTravelTime)) 
}

cmp <- function(base, ref) {
  full_join(base, ref, by="linkId") %>%
      mutate(vol_diff=vol.y - vol.x) %>%
      mutate(tt=((tt.y/tt.x) - 1)*100) %>%
      mutate(width=if_else(vol_diff <= 1, 1, 1 + log10(vol_diff))) %>%
      replace(is.na(.), 0)
}


tmap_mode("plot")


# http://tools.geofabrik.de/calc/#type=geofabrik_standard&bbox=6.782607,51.224478,6.836067,51.246109&tab=1&proj=EPSG:4326&places=3

# https://stackoverflow.com/questions/66889558/rotate-ggplot2-maps-to-arbitrary-angles

# Area of interest
bbox <- bb(c(6.781, 51.22, 6.835, 51.245)) # zoom: 16
#bbox <- bb(c(6.693, 51.175, 6.875, 51.278))

osm <- read_osm(bbox, zoom = 16, type = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")


base <- read_scenario("baseline")
lt <- read_scenario("lt-donothing")
lt_rd <- read_scenario("lt-reduce")


df <- cmp(base, lt_rd)
sdf <- merge_geom(df, geom)


tm_shape(osm) + tm_rgb() +
  tm_shape(sdf) +
  tm_lines(col = "vol_diff", lwd = "width", scale = 5, style ="cont", breaks = c(-100, 100), palette = "-RdYlGn") +
  tm_layout(main.title = "Volume")


