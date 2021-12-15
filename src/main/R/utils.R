
library(tidyverse)
library(xml2)
library(sf)

read_sim <- function(f) {
  
  p <- file.path(f, "analysis-v3.2", "person-trip-data")
  p <- list.files(p, pattern=".+trip_info_all_transport_modes_TRIPFILTER.+.csv", full.names = T)
  
  df <- read_delim(p, delim = ";", 
                   col_types = cols(
                     `person Id` = col_character()
                   )) %>%
    filter(`trip main mode`!="freight") %>%
    mutate(wkt=paste("LINESTRING(", `origin X coordinate (trip)`, `origin Y coordinate (trip)`, ",", 
                     `destination X coordinate (trip)`, `destination Y coordinate (trip)`, ")"))
  return(df)
  #return(st_as_sf(df, wkt="wkt", crs=25832))
}

read_trips <- function(f, crs=25832) {
  trips <- read_delim(list.files(f, pattern = "*.output_trips.csv.gz", full.names = T, include.dirs = F), delim = ";", trim_ws = T, 
                      col_types = cols(
                        person = col_character()
                      )) %>%
    mutate(wkt=paste("LINESTRING(", start_x, start_y, ",", end_x, end_y, ")"))
  
  return(st_as_sf(trips, wkt="wkt", crs=crs) %>% select(-wkt))
}

read_legs <- function(f, crs=25832) {
  legs <- read_delim(list.files(f, pattern = "*.output_legs.csv.gz", full.names = T, include.dirs = F), delim = ";", trim_ws = T, 
                      col_types = cols(
                        person = col_character()
                      )) %>%
    mutate(wkt=paste("LINESTRING(", start_x, start_y, ",", end_x, end_y, ")"))
  
  return(st_as_sf(legs, wkt="wkt", crs=crs)) %>% select(-wkt)
}

read_link_stats <- function(f) {
  
  p <- file.path(f, "linkStats.csv.gz")
  df <- read_csv(p) %>%
    filter(!str_starts(linkId, "pt_"))
  
  return (df)
}

read_link_geom <- function(f, crs, simplify=0) {
  
  geom <- read_csv(f)
  
  wkt <- geom %>% 
    mutate(Geometry=str_replace_all(Geometry, "\\),\\(", "|")) %>%
    mutate(Geometry=str_replace_all(Geometry, ",", " ")) %>%
    mutate(Geometry=str_replace_all(Geometry, "\\|", ", ")) %>%
    mutate(Geometry=str_replace(Geometry, "\\(", "LINESTRING\\("))
  
  gf <- st_as_sf(wkt, wkt="Geometry", crs=crs)
  
  if (simplify > 0) {
    return(simplify_shape(gf, fact = simplify))    
  } else {
    return(gf)
  }
}

read_network <- function(f) {
 
  doc <- read_xml(f)
  
  links <- xml_find_all(doc, "//link") %>%
    xml2::xml_attrs() %>%
    purrr::map_df(~as.list(.)) %>%
    readr::type_convert()
  
  nodes <- xml_find_all(doc, "//node") %>%
    xml2::xml_attrs() %>%
    purrr::map_df(~as.list(.)) %>%
    readr::type_convert()
  
  # TODO: attribute name = text()
  
  return(list(nodes=nodes, links=links))
}

merge_geom <- function(df, geom, by.x="linkId", by.y="LinkId", fact = 0) {
  
  # x and y are reversed because arguments are reversed
  df <- merge(geom, df, by.y=by.x, by.x=by.y, all.x = F, all.y=T)
  
  return(df)
}
