
library(tidyverse)
library(lubridate)
library(viridis)
library(sf)
library(tmap)
library(tmaptools)
library(fs)
library(ggpubr)
library(ggridges)
library(ggalluvial)
library(gridExtra)
library(ggsci)


# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")

source("theme.R")
# https://www.datanovia.com/en/blog/ggplot-colors-best-tricks-you-will-love/

theme_set(theme_Publication(18))
#theme_set(theme_pubr(base_size = 18))

source("utils.R")

shape <- st_read("../../../scenarios/input/shapeFiles/duesseldorf-city/duesseldorf-area.shp", crs=25832)

agg_sim <- function(df) {
  
  agg <- st_join(shape, df, left=F, join=st_contains) %>%
    st_set_geometry(NULL) %>%
    mutate(traveled_time=as.double(trav_time)) %>%
    group_by(main_mode) %>%
    summarise(distance=sum(traveled_distance), time=sum(traveled_time), n=n()) %>%
    mutate(share=n/sum(n))
  
  return(agg)
}


base <- read_trips("../../../output/base/")


cav100 <- read_trips("../../../output/cap-1.8/")
cav100_nomc <- read_trips("../../../output/cap-1.8-noMc/")

base_agg <- agg_sim(base)
cav100_agg <- agg_sim(cav100)
cav100_nmc_agg <- agg_sim(cav100_nomc)

# disaggregate mode shift -----

swith_to_car <- cav100 %>%
  semi_join(
    left_join(st_drop_geometry(cav100_nomc), st_drop_geometry(cav100), by =
                "trip_id") %>%
      filter(main_mode.x != "freight" & !is.na(main_mode.y)) %>%
      filter(main_mode.x != "ride") %>%
      filter(main_mode.y == "car", main_mode.x != "car") %>%
      select(trip_id)
  ) 

st_write(swith_to_car,"../../../output/cap-1.8/switch2car.shp")

switch_to_car_before_after <- 
left_join(st_drop_geometry(cav100_nomc), st_drop_geometry(cav100), by =
            "trip_id") %>%
  filter(main_mode.x != "freight" & !is.na(main_mode.y)) %>%
  filter(main_mode.x != "ride") %>%
  filter(main_mode.y == "car", main_mode.x != "car") 
  

stick_with_car_before_after <- 
left_join(st_drop_geometry(cav100_nomc), st_drop_geometry(cav100), by =
            "trip_id") %>%
  filter(main_mode.x != "freight" & !is.na(main_mode.y)) %>%
  filter(main_mode.x != "ride") %>%
  filter(main_mode.y == "car", main_mode.x == "car") 
  
detours_from_euclidean <- 
switch_to_car_before_after %>% 
  transmute(
    detour_from_euclidean_before = traveled_distance.x/euclidean_distance.x,
    detour_from_euclidean_after = traveled_distance.y/euclidean_distance.y
  ) 
  
detours_from_euclidean_stick <- 
stick_with_car_before_after %>% 
  transmute(
    detour_from_euclidean_before = traveled_distance.x/euclidean_distance.x,
    detour_from_euclidean_after = traveled_distance.y/euclidean_distance.y
  ) 

qplot(data=detours_from_euclidean, x= detour_from_euclidean_before, y = detour_from_euclidean_after)
qplot(data=detours_from_euclidean%>% 
        filter(detour_from_euclidean_before>1,
               detour_from_euclidean_before<2), x= detour_from_euclidean_before )
qplot(data=detours_from_euclidean %>% 
        filter(detour_from_euclidean_after>1,
               detour_from_euclidean_after<2), x= detour_from_euclidean_after )
qplot(data=detours_from_euclidean_stick%>% 
        filter(detour_from_euclidean_before>1,
               detour_from_euclidean_before<2), x= detour_from_euclidean_before )
qplot(data=detours_from_euclidean_stick %>% 
        filter(detour_from_euclidean_after>1,
               detour_from_euclidean_after<2), x= detour_from_euclidean_after )
qplot(data=detours_from_euclidean_stick, x= detour_from_euclidean_before, y = detour_from_euclidean_after, geom="stat_summary_2d")
ggplot(data=detours_from_euclidean_stick, aes(x= detour_from_euclidean_before, y = detour_from_euclidean_after))+
  stat_summary_hex(fun=~ n)


switch_to_car_out <- 
  swith_to_car %>%
  st_drop_geometry() %>% 
  left_join(
    swith_to_car %>%
      st_drop_geometry() %>%
      select(start_x, start_y, trip_id) %>%
      st_as_sf(
        coords = c("start_x", "start_y"),
        crs = st_crs("epsg:25832")
      ) %>%
      st_transform(4326) %>%
      mutate(start_lon = st_coordinates(.)[, 1],
             start_lat = st_coordinates(.)[, 2]) %>%
      st_drop_geometry() %>%
      left_join(
        swith_to_car %>%
          st_drop_geometry() %>%
          select(end_x, end_y, trip_id) %>%
          st_as_sf(
            coords = c("end_x", "end_y"),
            crs = st_crs("epsg:25832")
          ) %>%
          st_transform(4326) %>%
          mutate(end_lon = st_coordinates(.)[, 1],
                 end_lat = st_coordinates(.)[, 2]) %>%
          st_drop_geometry()
      )
  ) %>%
  mutate(
    start_x= start_lon,
    start_y= start_lat,
    end_x= end_lon,
    end_y= end_lat
  ) %>%
  select(-start_lon
         ,-start_lat
         ,-end_lon
         ,-end_lat)


  write_delim(x=switch_to_car_out, file="../../../output/cap-1.8/switch2car_lonlat.output_trips.csv.gz", delim = ";")


##################################

change <- left_join(st_drop_geometry(base), st_drop_geometry(cav100), by="trip_id") %>%
    filter(main_mode.x != "freight" & !is.na(main_mode.y)) %>%
    filter(main_mode.x != "ride") %>%
    group_by(main_mode.x, main_mode.y) %>%
    summarise(n=n()) %>%
    rename(source=main_mode.x, target=main_mode.y)

write_delim(x = change, file = "shift.csv", delim = ";")

ggplot(data = change,
       aes(axis1 = source, axis2 = target,
           y = n)) +
  scale_x_discrete(limits = c("Source", "Target"), expand = c(.2, .05)) +
  geom_alluvium() +
  geom_stratum() +
  geom_text(stat = "stratum", aes(label = after_stat(stratum))) +
  theme_minimal()



##################################

network <- read_network("../../../output/cap-1.8/dd.output_network.xml.gz")

network_base <- read_network("../../../output/base/dd.output_network.xml.gz")

link_stats <- read_link_stats("../../../output/base")

# this is too heavy, better to write out and join in qgis
# geom <- read_link_geom("../../../output/cap-1.8/dd.output_network.geo.json.gz", 25832, simplify = 0)

links <- network$links %>%
        left_join(network_base$links, by = "id") %>%
        mutate(diff=capacity.x-capacity.y)

df <- link_stats %>% group_by(linkId) %>%
  summarise(tt=mean(avgTravelTime), count=sum(vol_car)) %>%
  left_join(links, by=c("linkId"="id"))


# df <- merge_geom(df, geom)

tmap_mode("view")

tm_shape(df) +
  tm_lines(col = "diff", style = "cont", midpoint = 0, breaks = c(-2000, 2000), lwd = 3.5)


ggplot(filter(df, diff != 0), aes(x=diff)) + geom_histogram()

###################################

