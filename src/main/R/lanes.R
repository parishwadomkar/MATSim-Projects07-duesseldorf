

library(tidyverse)

# setwd("C:/Users/chris/Development/matsim-scenarios/matsim-duesseldorf/src/main/R")


read_flow <- function(flnm) {
  m <- str_extract_all(flnm, "\\d+")[[1]]
  read_csv(flnm) %>% 
    mutate(cv=as.numeric(m[1]), acv=as.numeric(m[2]), av=as.numeric(m[3]))
}

f <- "\\\\sshfs.kr\\rakow@cluster.math.tu-berlin.de\\net\\ils\\matsim-duesseldorf\\sumo\\output"

tbl <- list.files(f, pattern = "*.csv", full.names = T) %>% 
      map_df(~read_flow(.))

sample <- read_csv("C:\\Users\\chris\\Development\\matsim-scenarios\\matsim-duesseldorf\\src/main/python/sample.csv")


df <- tbl %>%
      left_join(sample) %>%
      filter(flow>0) %>%
      mutate(speed=speed*3.6)

tf <- df %>% filter(av==0) %>% 
  group_by(speed) %>%
  mutate(base=mean(flow[cv==100])) %>%
  ungroup()
  
ggplot(tf, aes(speed, flow, color=cv)) +
  ylab("Flow capacity [veh/h]") +
  xlab("Allowed speed [km/h]") +
  geom_point()

tf <- mutate(tf, speed=as.factor(round(speed))) %>%
      mutate(cv2=cv*cv) %>%
      mutate(cv3=cv*cv*cv)

x <- seq(0, 100, 1)

res <- tf %>%
  nest_by(speed) %>%
  mutate(mod = list(lm(flow ~ cv + cv2 + cv3, data = data))) %>%
  mutate(x=list(x), pred= list(predict(mod, list(cv=x, cv2=x^2, cv3=x^3)))) %>%
  unnest(cols=c(pred, x))


ggplot(tf, aes(cv, flow, color=speed)) +
  xlab("Share of CV") +
  ylab("Flow capacity [veh/h]") +
  geom_point() +
  geom_line(data=res, aes(x, pred, color=speed))

res <- tf %>%
    group_by(speed, cv) %>%
    summarise(rel=mean(flow) / base)

write_csv(res, 'acv.csv')

ggplot(res, aes(cv, rel, color=speed)) +
  xlab("Share of CV") +
  ylab("Rel. flow capacity") +
  geom_point()
#  geom_line(data=res, aes(x, pred, color=speed))


# Train model


train <- res %>%
          mutate(speed=as.numeric(speed)) %>%
          mutate(cv2=cv*cv)


#require(xgboost)
#dtrain <- xgb.DMatrix(data = as.matrix(train), label = res$rel)
#model <- xgboost(data = dtrain, nrounds = 200, early_stopping_rounds = 10, objective = "reg:squarederror", verbose = 1)


model <- lm(rel ~ speed + cv + cv2, train)


x <- data.frame(speed=seq(5, 140, 10))
y <- data.frame(cv=seq(0, 100, 5))

test <- crossing(x, y) %>%
          mutate(cv2=cv*cv)
#dtest <- xgb.DMatrix(data = as.matrix(test))

pred <- test %>% add_column(rel = predict(model, test))

ggplot(pred, aes(cv, rel, color=speed)) +
  xlab("Share of CV") +
  ylab("Predicted rel. flow capacity") +
  geom_point()


