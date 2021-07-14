
library(tidyverse)
library(lubridate)
library(viridis)
library(sf)
library(tmap)
library(fs)
library(ggpubr)
library(ggridges)
library(ggalluvial)
library(gridExtra)
library(ggsci)

# setwd("C:/Users/chris/Dropbox/Uni/Dissertation/Paper/KomodNext/data")

source("theme.R")
# https://www.datanovia.com/en/blog/ggplot-colors-best-tricks-you-will-love/

theme_set(theme_Publication(18))
#theme_set(theme_pubr(base_size = 18))

### Fig. 1

srv <- read_delim("srv.csv", delim = ";") %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
                names_to="mode",
                values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car"))

srv_sum <- sum(srv$trips)
srv_aggr <- srv %>%
  group_by(mode) %>%
  summarise(share=sum(trips) / srv_sum)

p1_aggr <- ggplot(data=srv_aggr, mapping =  aes(x=1, y=share, fill=mode)) +
  labs(subtitle = "Survey data") +
  geom_bar(position="fill", stat="identity") +
  coord_flip() +
  geom_text(aes(label=scales::percent(share, accuracy = 0.1)), size= 5, position=position_fill(vjust=0.5)) +
  scale_fill_locuszoom() +
  theme_void() +
  theme(legend.position="none")


dist_order_srv <- factor(srv$distance, level = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000", "10000+"))

p1 <- ggplot(srv, aes(fill=mode, y=trips, x=dist_order_srv)) +
  labs(subtitle = "Survey data", x="distance [m]") +
  geom_bar(position="stack", stat="identity") +
  scale_fill_locuszoom()

#calib <- read_delim("calib.csv", delim = ";") %>%
#  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
#               names_to="mode",
#               values_to="trips") %>%
#  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car"))

calib <- read_delim("calib15.csv", delim = ";", trim_ws = T) %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
               names_to="mode",
               values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(dist_group=sprintf("%g - %g", `distance - from [m]`, `distance to [m]`)) %>%
  mutate(dist_group=case_when(
    `distance to [m]`== max(`distance to [m]`) ~ sprintf("%g+", `distance - from [m]`),
    TRUE ~ `dist_group`
  ))

dist_order <- factor(calib$dist_group, level = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000"))
dist_order <- fct_explicit_na(dist_order, "10000+")


calib_sum <- sum(calib$trips)
calib_aggr <- calib %>%
  group_by(mode) %>%
  summarise(share=sum(trips) / calib_sum)

p2 <- ggplot(calib, aes(fill=mode, y=trips, x=dist_order)) +
  labs(subtitle = "Calibrated scenario", x="distance [m]") +
  geom_bar(position="stack", stat="identity") +
  scale_fill_locuszoom()


g <- arrangeGrob(p1, p2, ncol = 2)
ggsave(filename = "modal-split.png", path = "../imgs", g,
       width = 15, height = 5, device='png', dpi=300)


#
# Next figure
#

split <- read.csv("modal_split.csv")

trips <- read_delim("validated_trips.csv", ";") %>%
          mutate(error=traveltimeActual / traveltimeValidated - 1) %>%
          filter(traveltimeValidated > 0)

mean(trips$error)

p2 <- ggplot(data=trips, mapping = aes(x=traveltimeActual, y=traveltimeValidated, color=error)) +
  labs(x="Simulated travel times [s]", y="Validated travel times [s]") + 
  geom_point() +
  scale_color_viridis_c(direction = -1, limit=c(-1, 1)) +
  geom_segment(aes(x = 0, y = 0, xend = 6000, yend = 6000), color="black", linetype=2) +
  theme(legend.position = "right", legend.direction = "vertical") +
  guides(fill=guide_legend(position="top")) +
  xlim(NaN, 6000)


show(p2)

ggsave(filename = "trips.png", p2, width = 10, height = 5, device='png', dpi=300)

g <- arrangeGrob(p1, p2, ncol = 2)

ggsave(filename = "calibration.png", path = "../imgs", g,
       width = 15, height = 5, device='png', dpi=300)


#### Fig Lanes

cap <- read_delim(file = "big/KoMoDnext_Q_at_LSA_SUMO-TUB_20201228.csv.gz", ";", 
                  col_types = cols(junctionId = col_character()))

lanes <- read_csv(file ="big/lanes.csv.gz") 

# enum ("s" = straight, "t" = turn, "l" = left, "r" = right, "L" = partially left, R = partially right, "invalid" = no direction)
aggr_dir <- function(x) {
  sapply(x, function(d){
  if (d == "l" || d == "L")
    return("left")
  else if (d == "R" || d == "r")
    return("right")
  else if (d == "s")
    return("straight")
  else return("turn")
  })
}

df <- lanes %>%
  right_join(cap, by = c("fromLaneId", "toLaneId")) %>%
  mutate(direction=aggr_dir(dir)) %>%
  filter(direction!="turn")


p1 <- ggplot(data=df, mapping = aes(y = intervalVehicleSum, fill=direction)) +
  labs(y="traffic flow [veh/h]") + 
  geom_histogram() +
  coord_flip() + 
  scale_fill_locuszoom()


p2 <- ggplot(df, aes(x = direction, y = intervalVehicleSum, fill = direction)) +
  labs(y="traffic flow [veh/h]", x="") +
  ylim(500, NA) +
  geom_violin(trim=F) +
  geom_boxplot(width=0.1, fill="white") +
  scale_fill_locuszoom() +
  theme(legend.position = "none")
show(p2)


g <- arrangeGrob(p1, p2, widths=c(0.6, 0.4), ncol = 2)

ggsave(filename = "traffic_flow.png", path = "../imgs", g,
       width = 15, height = 5, device='png', dpi=300)


### Fig results

files <- fs::dir_ls("big/", type = "dir") %>%
  fs::dir_ls(glob = "*trip_info_car*.csv") 

trips <- files %>%
  map_dfr(read_delim, delim=";", .id = "source") %>%
  mutate(scenario=str_match(source, "big/([a-z0-9.\\-]+)/")[,2]) %>%
  mutate(speed=(`travel distance (trip) [m]` / 1000)/ (`travel time (trip) [sec]` / 3600)) %>%
  select(-source)

# base travel time
bt = 37513728
# base distance
bd = 275683503

trips %>%
  group_by(scenario) %>%
  summarise(tt=sum(`travel time (trip) [sec]`), dd=sum(`travel distance (trip) [m]`), kmh=mean(speed, na.rm = T)) %>%
  mutate(rt=100*(tt/bt) -100, rd=100*(dd/bd) -100)

# plot for speed
trips %>%
  filter(scenario=="1.4-no-mc" | scenario=="0.6-no-mc" | scenario=="calib") %>%
  filter(`travel distance (trip) [m]` > 10) %>%
  ggplot(aes(x = speed, fill = scenario)) +
  scale_fill_viridis(discrete=TRUE) +
  scale_color_viridis(discrete=TRUE) +
  xlim(0, 60) +
  geom_density(adjust=1.5)

# NOT USED
trips %>%
  filter(scenario=="1.4" | scenario=="1.2") %>%
  ggplot(aes(x=travelTime, y=distance)) +
  
  stat_density_2d(geom = "polygon", aes(alpha = ..level.., fill = scenario))

### This one is not so helpful

shift <- read_csv("big/aggrModalSplitShift.csv") %>%
    rename(`1.0`=calib) %>%
    #filter(`0.6`=="carf" | `1.0`== "car" | `1.4` == "carf") %>%
    select(-blank)


lodes <- to_lodes_form(as.data.frame(shift),
                           axes = 2:3,
                           id = "Cohort")

ggplot(data = lodes, 
       aes(x = x, y = freq, label=stratum,
           stratum = stratum, alluvium=Cohort, fill=stratum)) +
  scale_x_discrete(expand = c(.1, .1)) +
  geom_flow() +
  geom_stratum(alpha = .5) +
  geom_text(stat = "stratum", size = 3) +
  scale_fill_locuszoom()



