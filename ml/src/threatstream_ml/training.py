"""Train interpretable CIC classifiers without touching the held-out test split."""
from __future__ import annotations
import argparse, json, time
from datetime import datetime, timezone
from pathlib import Path
import joblib
import numpy as np
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from . import MODEL_FORMAT_VERSION
from .analysis import load_csvs
from .artifacts import sha256
from .preprocessing import prepare_frame
from .schema import FEATURE_COLUMNS

RANDOM_SEED = 202518

def _assert_stratifiable(labels):
    counts = labels.value_counts()
    if len(counts) < 2 or counts.min() < 3:
        raise ValueError("training requires at least two labels and three rows in every class for stratified train/validation/test splits")

def _pipeline(model):
    return Pipeline([("preprocessing", ColumnTransformer([("numbers", Pipeline([("impute", SimpleImputer(strategy="median")), ("scale", StandardScaler())]), list(FEATURE_COLUMNS))], remainder="drop")), ("classifier", model)])

def _evaluation(model, x, y) -> dict:
    started=time.perf_counter(); predicted=model.predict(x); elapsed=time.perf_counter()-started
    return {"accuracy": float(accuracy_score(y,predicted)), "f1Macro": float(f1_score(y,predicted,average="macro",zero_division=0)), "f1Weighted": float(f1_score(y,predicted,average="weighted",zero_division=0)), "classificationReport": classification_report(y,predicted,output_dict=True,zero_division=0), "confusionMatrix": confusion_matrix(y,predicted,labels=sorted(y.unique())).tolist(), "confusionMatrixLabels": sorted(y.unique()), "inferenceSeconds": elapsed, "inferenceRows": int(len(x))}

def train(dataset: Path, artifact_dir: Path, max_rows: int | None = None) -> dict:
    raw=load_csvs(dataset,max_rows); x,y=prepare_frame(raw); _assert_stratifiable(y)
    # Row-wise stratification is reproducible, but CIC captures may contain temporal/flow duplicates;
    # metrics are explicitly offline estimates, not a deployment claim.
    x_trainval,x_test,y_trainval,y_test=train_test_split(x,y,test_size=.20,random_state=RANDOM_SEED,stratify=y)
    x_train,x_validation,y_train,y_validation=train_test_split(x_trainval,y_trainval,test_size=.25,random_state=RANDOM_SEED,stratify=y_trainval)
    candidates={"logistic-regression": _pipeline(LogisticRegression(max_iter=1000,class_weight="balanced",random_state=RANDOM_SEED)), "random-forest": _pipeline(RandomForestClassifier(n_estimators=200,class_weight="balanced_subsample",random_state=RANDOM_SEED,n_jobs=-1,min_samples_leaf=2))}
    validation={}; fitted={}
    for name, model in candidates.items():
        started=time.perf_counter(); model.fit(x_train,y_train); validation[name]={"trainingSeconds":time.perf_counter()-started, **_evaluation(model,x_validation,y_validation)}; fitted[name]=model
    selected=max(validation,key=lambda name: validation[name]["f1Macro"])
    final=candidates[selected]; started=time.perf_counter(); final.fit(x_trainval,y_trainval); training_seconds=time.perf_counter()-started
    test=_evaluation(final,x_test,y_test); test["trainingSeconds"]=training_seconds
    artifact_dir.mkdir(parents=True,exist_ok=True); model_path=artifact_dir/"model.joblib"; joblib.dump(final,model_path)
    version="cicids2017-"+datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    result={"modelVersion":version,"randomSeed":RANDOM_SEED,"featureColumns":list(FEATURE_COLUMNS),"labelMapping":{"modelClasses":list(map(str,final.named_steps["classifier"].classes_)),"benignLabel":"BENIGN (case-insensitive CIC label)","predictionPolicy":"BENIGN class is benign; all other CIC labels are ATTACK."},"rows":{"raw":len(raw),"labeled":len(y),"train":len(y_train),"validation":len(y_validation),"test":len(y_test)},"classDistribution":{str(k):int(v) for k,v in y.value_counts().items()},"selectionMetric":"validation macro F1","selectedModel":selected,"validation":validation,"heldOutTest":test,"limitations":["Offline row-stratified evaluation can overestimate production performance when capture-specific or duplicate flows cross splits.","No risk score is derived from model confidence."]}
    metadata={"formatVersion":MODEL_FORMAT_VERSION,"modelVersion":version,"featureColumns":list(FEATURE_COLUMNS),"modelSha256":sha256(model_path),"createdAt":datetime.now(timezone.utc).isoformat(),"training":result}
    (artifact_dir/"metrics.json").write_text(json.dumps(result,indent=2,default=float),encoding="utf-8"); (artifact_dir/"metadata.json").write_text(json.dumps(metadata,indent=2,default=float),encoding="utf-8")
    return result

def main() -> None:
    parser=argparse.ArgumentParser(); parser.add_argument("path",type=Path); parser.add_argument("--artifact-dir",type=Path,default=Path("ml/artifacts/cicids2017-v1")); parser.add_argument("--max-rows",type=int); args=parser.parse_args(); print(json.dumps(train(args.path,args.artifact_dir,args.max_rows),indent=2,default=float))
if __name__ == "__main__": main()
