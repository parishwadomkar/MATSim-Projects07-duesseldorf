#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import pandas as pd
import xgboost as xgb
import optuna

from sklearn.metrics import mean_absolute_percentage_error

#%%

params = {
        # Parameters that we are going to tune.
        'eta': 0.036,
        'lambda':  0.744,
        'alpha': 0.764,
        'gamma': 1,
        'max_depth': 5,
        'min_child_weight': 1,
        'subsample': 0.882,
        'colsample_bytree': 0.914,
        # Other parameters
        'objective': 'reg:squarederror',
 #       'eval_metric' : 'rmsle',
#        'num_class': 3 if args.objective == "class" else 1,
        # Weight the positive samples much higher
        #'scale_pos_weight': 5,
        'tree_method': 'gpu_hist'
}


#%%

lanes = pd.read_csv("lanes.csv.gz")

capacities = pd.read_csv("../../../KoMoDnext_Q_at_LSA_SUMO-TUB_20201228.csv.gz", sep=";")

joined = lanes.merge(capacities, on=("fromLaneId", "toLaneId"))

def encode_and_bind(original_dataframe, feature_to_encode):
    dummies = pd.get_dummies(original_dataframe[[feature_to_encode]])
    res = pd.concat([original_dataframe, dummies], axis=1)
    res = res.drop([feature_to_encode], axis=1)
    return(res) 


def create_ds(joined):

    for c in ("dir", "state", "edgeType"):
        joined = encode_and_bind(joined, c)
        
    df = joined.drop(columns=['junctionId_x', 'fromEdgeId_x', 'toEdgeId_x', 'fromLaneId', 'toLaneId',
       'junctionType', 'junctionId_y', 'linkIndex', 'fromEdgeId_y', 'toEdgeId_y', 'intervalVehicleSum',
        # drop uncommon columns
        'edgeType_highway.motorway', 'state_m', 'state_M'
       ], errors='ignore') 

    
    label = joined.intervalVehicleSum
    
    data = xgb.DMatrix(df, label=label, enable_categorical=True)
    
    return data

#%%

data = create_ds(joined)


#%%

def objective(trial):
    
    param = params.copy()

    param.update({
        'eta': trial.suggest_loguniform('eta', 0.01, 1),
        'lambda': trial.suggest_loguniform('lambda', 1e-4, 1),
        'alpha': trial.suggest_loguniform('alpha', 1e-4, 1.0),
        'gamma': trial.suggest_categorical('gamma', [0, 1, 5, 20, 70]),
        'max_depth': trial.suggest_int('max_depth', 2, 5),
        'min_child_weight': trial.suggest_categorical('min_child_weight', [0.5, 1, 5, 8, 20, 50]),
        'subsample': trial.suggest_loguniform('subsample', 1e-4, 1),
        'colsample_bytree': trial.suggest_loguniform('colsample_bytree', 1e-4, 1),
    })
    
    model = xgb.train(param, data, num_boost_round=50000, verbose_eval=False, evals=[(X_test, "Test")],
                     early_stopping_rounds=500
    )
    
    err = mean_absolute_percentage_error(X_test.get_label(), model.predict(X_test))
    
    return err
    
    #return model.best_score
            
    #loss = 'test-rmse-mean'
    #return cv_results[loss].min()


#%%

study = optuna.create_study(direction='minimize')
study.optimize(objective, n_trials=300)


#%%

# Validation

from lane_capacity import read_network

hh = read_network("../../../../matsim-hamburg/scenarios/input/hamburg-sumo.net.xml")

hh_cap = pd.read_csv("RLHH_analyze_Q_at_LSA_all.csv", sep=";")

#%%

joined = hh.merge(hh_cap, on=("fromLaneId", "toLaneId"))

X_test = create_ds(joined)


#%%

model = xgb.train(
    params, data, num_boost_round=50000, verbose_eval=True, evals=[(X_test, "Test")],
    early_stopping_rounds=50
)

importance = model.get_score(importance_type='gain')

model.save_model("capacities.xgb")


#%%

from xgboost import plot_tree

plot_tree(model)
