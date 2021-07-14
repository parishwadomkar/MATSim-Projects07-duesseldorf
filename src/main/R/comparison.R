
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

agg_sim <- function(df) {
  
  agg <- df %>%
    group_by(`trip main mode`) %>%
    summarise(distance=sum(`travel distance (trip) [m]`), time=sum(`travel time (trip) [sec]`), n=n()) %>%
    mutate(share=n/sum(n))
  
  return(agg)
}


base <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\base")
#base <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\old\\output\\v1.5-25pct-026-dc_1.14-no-lanes")


cap06_mc <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-0.6")
cap08_mc <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-0.8")
cap12_mc <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-1.2")
cap14_mc <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-1.4")


cap06 <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-0.6-noMC")
cap08 <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-0.8-noMC")
cap12 <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-1.2-noMC")
cap14 <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-1.4-noMC")


av100 <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\av-100-noMC")
av50 <- read_sim("\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\av-050-noMC")


base_agg <- agg_sim(base)
cap06_mc_agg <- agg_sim(cap06_mc)
cap08_mc_agg <- agg_sim(cap08_mc)
cap12_mc_agg <- agg_sim(cap12_mc)
cap14_mc_agg <- agg_sim(cap14_mc)
cap06_agg <- agg_sim(cap06)
cap08_agg <- agg_sim(cap08)
cap12_agg <- agg_sim(cap12)
cap14_agg <- agg_sim(cap14)


av100_agg <- agg_sim(av100)
av50_agg <- agg_sim(av50)

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

