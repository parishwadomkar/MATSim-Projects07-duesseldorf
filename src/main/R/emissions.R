
library(tmap)
library(tmaptools)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")

source("theme.R")
# https://www.datanovia.com/en/blog/ggplot-colors-best-tricks-you-will-love/

theme_set(theme_Publication(18))
#theme_set(theme_pubr(base_size = 18))

source("utils.R")

read_spatial <- function(f) {
  
  p <- file.path(f, "spatial_emissions.csv")
  df <- read_csv(p)
  
  return(st_as_sf(df, coords = c("x", "y"), crs=25832))
}

df <- read_spatial("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\experiment\\output\\base")


tmap_mode("plot")


tf <- filter(df, NOx > 0.002)

tmap_style("white")

bbox <- bb(c(6.693, 51.175, 6.875, 51.278))
osm <- read_osm(bbox, zoom = 13, type = "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")

tm_shape(osm) + tm_rgb() +
tm_shape(tf) +
  tm_squares(col = "NOx", border.lwd=NA, alpha=0.1)
