
library(tidyverse)
library(tmap)
library(tmaptools)
library(lubridate)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")


source("theme.R")
source("utils.R")

theme_set(theme_Publication(18))

# immission_without_barrier.csv.gz

df <- read_delim("C:\\Users\\chris\\Downloads\\immission_without_barrier.csv.gz", delim = ";") %>%
  group_by(x, y) %>%
  summarise(immission=mean(immission)) %>%
  filter(immission>20) %>%
  st_as_sf(coords = c("x", "y"), crs=25832)


bbox <- bb(c(6.693, 51.175, 6.875, 51.278))
osm <- read_osm(bbox, zoom = 13, type = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")

p <- get_brewer_pal("-PiYG", n = 10)

tm_shape(osm) + tm_rgb() +
  tm_shape(df) +
  tm_squares(col = "immission", border.lwd=NA, size = 0.4, alpha=0.2, palette=p)
