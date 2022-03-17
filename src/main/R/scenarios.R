

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

p <- "\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\experiment\\output\\"

read_scenario <- function(scenario) {
  read_link_stats(paste(p, scenario, sep="")) %>%
    group_by(linkId) %>%
    summarise(vol=sum(vol_car), tt=mean(avgTravelTime)) 
}

cmp <- function(base, ref) {
  full_join(base, ref, by="linkId") %>%
      mutate(vol_diff=vol.y - vol.x) %>%
      mutate(tt=((tt.y/tt.x) - 1)*100) %>%
      mutate(width=if_else(abs(vol_diff) <= 1, 1, 1 + log10(abs(vol_diff)))) %>%
      replace(is.na(.), 0)
}


tmap_mode("plot")


# http://tools.geofabrik.de/calc/#type=geofabrik_standard&bbox=6.782607,51.224478,6.836067,51.246109&tab=1&proj=EPSG:4326&places=3

# https://stackoverflow.com/questions/66889558/rotate-ggplot2-maps-to-arbitrary-angles

# Area of interest
bbox <- bb(c(6.781, 51.22, 6.835, 51.245)) # zoom: 16
bbox <- bb(c(6.776, 51.222, 6.839, 51.251))

#bbox <- bb(c(6.693, 51.175, 6.875, 51.278))

osm <- read_osm(bbox, zoom = 16, type = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")


base_nmc <- read_scenario("scenario-base--no-mc")
mt_nmc <- read_scenario("policy-scenario-mt-red_0--no-mc")
lt_nmc <- read_scenario("policy-scenario-lt-red_0--no-mc")
lt_rd_nmc <- read_scenario("policy-scenario-lt-red_1--no-mc")
lt_bike_nmc <- read_scenario("policy-scenario-lt-red_bike--no-mc")

base <- read_scenario("scenario-base")
lt <- read_scenario("policy-scenario-lt-red_0")
lt_rd <- read_scenario("policy-scenario-lt-red_1")
lt_bike <- read_scenario("policy-scenario-lt-red_bike")

mt <- read_scenario("policy-scenario-mt-red_0")
mt_rd <- read_scenario("policy-scenario-mt-red_1")

################

df <- cmp(base, lt_bike)
sdf <- merge_geom(df, geom)

tm_shape(osm) + tm_rgb() +
tm_shape(sdf) + tm_lines(col = "vol_diff", lwd = "width", scale = 5, style ="cont", breaks = c(-400, 400), midpoint = 0, 
         palette = "-RdYlGn", legend.lwd.show = F, legend.col.is.portrait = F,
         title.col = "Volume difference") +
  tm_layout(legend.bg.color = "white", legend.bg.alpha = 1)
