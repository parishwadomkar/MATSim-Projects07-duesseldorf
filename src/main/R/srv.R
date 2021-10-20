
library(tidyverse)
library(lubridate)
library(gridExtra)
library(viridis)
library(ggsci)
library(sf)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")

source("utils.R")

levels = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000", "10000+")
breaks = c(0, 1000, 3000, 5000, 10000, Inf)

shape <- st_read("../../../../public-svn/matsim/scenarios/countries/de/duesseldorf/duesseldorf-v1.0/original-data/duesseldorf-area-shp/duesseldorf-area.shp", crs=25832)


# Read SrV
##################

srv <- read_delim("srv.csv", delim = ";") %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike"),
                names_to="mode",
                values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "car")) %>%
  mutate(source="srv")


# agents in city 115209, younger population is missing
# scale factor 5.2 instead of 4

# Inhabitants times avg trip number
srv_scale <- 620000 * 3.5 / sum(srv$trips)

srv <- srv %>%
          mutate(share=trips / sum(srv$trips)) %>%
          mutate(scaled_trips=620000 * 3.5 * share)

write_csv(srv, "srv_raw.csv")

########### Execute either one

srv <- read_csv("srv_adj.csv") %>%
  mutate(main_mode=mode) %>%
  mutate(scaled_trips=620000 * 3.5 * share) %>%
  mutate(source = "srv") %>%
  mutate(dist_group=fct_relevel(dist_group, levels)) %>%
  arrange(dist_group)


# Read simulation
#######################

f <- "\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils4\\matsim-duesseldorf\\experiment\\output\\cap-1.0"
sim_scale <- 4

homes <- read_csv("../../../home.csv", col_types = cols(
                    person = col_character()
                  ))

persons <- read_delim(list.files(f, pattern = "*.output_persons.csv.gz", full.names = T, include.dirs = F), delim = ";", trim_ws = T, 
                      col_types = cols(
                        person = col_character(),
                        good_type = col_integer()
                      )) %>%
            right_join(homes) %>%
            st_as_sf(coords = c("home_x", "home_y"), crs = 25832) %>%
            st_filter(shape)

# Ride is merged with car
trips <- read_delim(list.files(f, pattern = "*.output_trips.csv.gz", full.names = T, include.dirs = F), delim = ";", trim_ws = T, 
                    col_types = cols(
                      person = col_character()
                    )) %>%
  filter(main_mode!="freight") %>%
  mutate(main_mode = recode(main_mode, `ride`="car")) %>%
  semi_join(persons) %>%
  mutate(dist_group = cut(traveled_distance, breaks=breaks, labels=levels)) %>%
  filter(!is.na(dist_group))

sim <- trips %>%
  group_by(dist_group, main_mode) %>%
  summarise(trips=n()) %>%
  mutate(mode = fct_relevel(main_mode, "walk", "bike", "pt", "car")) %>%
  mutate(scaled_trips=sim_scale * trips) %>%
  mutate(source = "sim")

write_csv(sim, "sim.csv")

######
# Total modal split
#######

srv_aggr <- srv %>%
  group_by(mode) %>%
  summarise(share=sum(share)) %>%  # assume shares sum to 1
  mutate(mode=fct_relevel(mode, "walk", "bike", "pt", "car"))  

aggr <- sim %>%
  group_by(mode) %>%
  summarise(share=sum(trips) / sum(sim$trips))

p1_aggr <- ggplot(data=srv_aggr, mapping =  aes(x=1, y=share, fill=mode)) +
  labs(subtitle = "Survey data") +
  geom_bar(position="fill", stat="identity") +
  coord_flip() +
  geom_text(aes(label=scales::percent(share, accuracy = 0.1)), size= 2, position=position_fill(vjust=0.5)) +
  scale_fill_locuszoom() +
  theme_void() +
  theme(legend.position="none")

p2_aggr <- ggplot(data=aggr, mapping =  aes(x=1, y=share, fill=mode)) +
  labs(subtitle = "Simulation") +
  geom_bar(position="fill", stat="identity") +
  coord_flip() +
  geom_text(aes(label=scales::percent(share, accuracy = 0.1)), size= 2, position=position_fill(vjust=0.5)) +
  scale_fill_locuszoom() +
  theme_void()

g <- arrangeGrob(p1_aggr, p2_aggr, ncol = 2)
ggsave(filename = "modal-split.png", path = ".", g,
       width = 12, height = 2, device='png', dpi=300)

#########
# Combined plot by distance
##########

total <- bind_rows(srv, sim)

# Maps left overgroups
dist_order <- factor(total$dist_group, level = levels)
dist_order <- fct_explicit_na(dist_order, "10000+")

ggplot(total, aes(fill=mode, y=scaled_trips, x=source)) +
  labs(subtitle = paste("Düsseldorf scenario", f), x="distance [m]") +
  geom_bar(position="stack", stat="identity", width = 0.5) +
  facet_wrap(dist_order, nrow = 1)



# Needed for calculating added short distance trips

calib_sum <- sum(calib$trips)
calib_aggr <- calib %>%
  group_by(dist_group) %>%
  summarise(share=sum(trips) / calib_sum)

# Needed share of trips
tripShare <- 0.2418
shortDistance <- sum(filter(calib, dist_group=="0 - 1000")$trips)
numTrips = (shortDistance - calib_sum * tripShare) / (tripShare - 1)



