
library(tidyverse)
library(lubridate)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")

# Read SrV
##################


srv <- read_delim("srv.csv", delim = ";") %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
                names_to="mode",
                values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(source="srv")


# Inhabitants times avg trip number

srv_scale <- 620000 * 3.5 / sum(srv$trips)

srv <- srv %>%
    mutate(scaled_trips=trips*srv_scale)


# agents in city 115209, younger population is missing
# scale factor 5.2 instead of 4


# Read simulation
#######################

f <- "005.csv"
sim_scale <- 10.0

calib <- read_delim(f, delim = ";", trim_ws = T) %>%
  pivot_longer(cols=c("pt", "walk", "car", "bike", "ride"),
               names_to="mode",
               values_to="trips") %>%
  mutate(mode = fct_relevel(mode, "walk", "bike", "pt", "ride", "car")) %>%
  mutate(dist_group=sprintf("%g - %g", `distance - from [m]`, `distance to [m]`)) %>%
  mutate(dist_group=case_when(
    `distance to [m]`== max(`distance to [m]`) ~ sprintf("%g+", `distance - from [m]`),
    TRUE ~ `dist_group`
  )) %>%
  mutate(scaled_trips=trips*sim_scale) %>%
  mutate(source="sim")



# Combined plot

total <- bind_rows(srv, calib)

dist_order <- factor(total$dist_group, level = c("0 - 1000", "1000 - 3000", "3000 - 5000", "5000 - 10000"))

# Maps left overgroups
dist_order <- fct_explicit_na(dist_order, "10000+")

ggplot(total, aes(fill=mode, y=scaled_trips, x=source)) +
  labs(subtitle = paste("Düsseldorf", f), x="distance [m]") +
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



