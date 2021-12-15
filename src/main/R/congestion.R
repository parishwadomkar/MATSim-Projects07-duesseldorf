
library(sf)
library(tmap)
library(tmaptools)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")

source("theme.R")

theme_set(theme_Publication(18))
#theme_set(theme_pubr(base_size = 18))

source("utils.R")


network <- read_network("../../../scenarios/input/duesseldorf-v1.6-network.xml.gz")

sim <- read_link_stats("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\base")


junctions <- read_csv("../../../CV-100_AV-000.csv", col_types = cols(
  junctionId = col_character()
))


links <- filter(network$links, id %in% junctions$fromEdgeId) %>%
          mutate(travelTime=length/freespeed)

df <- sim %>% right_join(links, by=c("linkId"="id")) %>%
          mutate(volume=4 * (vol_car+vol_freight)) %>%
          mutate(score=volume/capacity)

res <- df %>%
      group_by(linkId) %>%
      summarise(score=sum(score), to=first(to)) %>%
      ungroup() %>%
      drop_na() %>%
      arrange(desc(score)) %>%
      left_join(network$nodes, by = c("to"="id")) %>%
      st_as_sf(coords=c("x", "y"), crs=25832)


bbox <- bb(c(6.693, 51.175, 6.875, 51.278))

osm <- read_osm(bbox, zoom = 13, type = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")

tm_shape(osm) + tm_rgb() +
  tm_shape(top_n(res, 30, score)) +
  tm_bubbles(col = "score", size = 0.2)


write_csv(res, "../../../iintersections.csv")
