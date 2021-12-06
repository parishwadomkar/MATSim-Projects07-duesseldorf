
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

shape <- st_read("../../../../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp", crs=25832)

agg_sim <- function(df) {
  
  agg <- st_join(shape, df, left=F, join=st_contains) %>%
    st_set_geometry(NULL) %>%
    mutate(traveled_time=as.double(trav_time)) %>%
    group_by(main_mode) %>%
    summarise(distance=sum(traveled_distance), time=sum(traveled_time), n=n()) %>%
    mutate(share=n/sum(n))
  
  return(agg)
}


base <- read_trips("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\calibration\\runs\\003")


cav100 <- read_trips("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\experiment\\output\\cav-100")
cav100_nmc <- read_trips("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\experiment\\output\\cav-100-noMc")

base_agg <- agg_sim(base)
cav100_agg <- agg_sim(cav100)
cav100_nmc_agg <- agg_sim(cav100_nmc)

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

network <- read_network("../../../scenarios/input/duesseldorf-v1.6-network.xml.gz")

network_base <- read_network("../../../scenarios/input/duesseldorf-base-network.xml.gz")

link_stats <- read_link_stats("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\base")
geom <- read_link_geom("../../../scenarios/input/duesseldorf-v1.6-network-linkGeometries.csv", 25832, simplify = 0)

links <- network$links %>%
        left_join(network_base$links, by = "id") %>%
        mutate(diff=capacity.x-capacity.y)

df <- link_stats %>% group_by(linkId) %>%
  summarise(tt=mean(avgTravelTime), count=sum(vol_car)) %>%
  left_join(links, by=c("linkId"="id"))


df <- merge_geom(df, geom)

tmap_mode("view")

tm_shape(df) +
  tm_lines(col = "diff", style = "cont", midpoint = 0, breaks = c(-2000, 2000), lwd = 3.5)


ggplot(filter(df, diff != 0), aes(x=diff)) + geom_histogram()

###################################

