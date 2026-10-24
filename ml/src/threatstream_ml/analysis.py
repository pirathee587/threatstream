"""Reproducible, bounded CIC-IDS2017 profiling command."""
from __future__ import annotations
import argparse, json
from pathlib import Path
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from .preprocessing import prepare_frame, resolve_feature_columns, resolve_label_column

def load_csvs(path: Path, max_rows: int | None = None) -> pd.DataFrame:
    files = [path] if path.is_file() else sorted(path.rglob("*.csv"))
    if not files: raise FileNotFoundError(f"no CSV files under {path}")
    frames, remaining = [], max_rows
    for file in files:
        frame = pd.read_csv(file, low_memory=False)
        if remaining is not None:
            frame, remaining = frame.iloc[:remaining], max(0, remaining - len(frame))
        frames.append(frame)
        if remaining == 0: break
    return pd.concat(frames, ignore_index=True)

def analyse(path: Path, report_dir: Path, max_rows: int | None = None) -> dict:
    report_dir.mkdir(parents=True, exist_ok=True)
    raw = load_csvs(path, max_rows)
    label_column = resolve_label_column(raw.columns)
    feature_mapping = resolve_feature_columns(raw.columns)
    raw_numeric = pd.DataFrame({target: pd.to_numeric(raw[source], errors="coerce") for target, source in feature_mapping.items()})
    features, labels = prepare_frame(raw)
    numeric = features.replace([np.inf, -np.inf], np.nan)
    counts = labels.value_counts().sort_values(ascending=False)
    benign = int(labels.str.casefold().eq("benign").sum())
    summary = {"rowsRead": len(raw), "columns": list(map(str, raw.columns)), "dtypes": {str(k): str(v) for k,v in raw.dtypes.items()}, "duplicateRows": int(raw.duplicated().sum()), "infiniteFeatureValues": int(np.isinf(raw_numeric.to_numpy(dtype=float)).sum()), "missingValues": {str(k): int(v) for k,v in raw.isna().sum().items() if v}, "classDistribution": counts.to_dict(), "benignRows": benign, "attackRows": int(len(labels)-benign), "usableLabeledRows": len(labels), "modelFeatureMissingValues": {k:int(v) for k,v in numeric.isna().sum().items()}}
    (report_dir / "dataset_summary.json").write_text(json.dumps(summary, indent=2, default=str), encoding="utf-8")
    counts.plot.bar(figsize=(max(8, len(counts)*.45), 5), title="CIC-IDS2017 attack-category distribution"); plt.tight_layout(); plt.savefig(report_dir / "attack_category_distribution.png", dpi=160); plt.close()
    pd.Series({"BENIGN": benign, "ATTACK": len(labels)-benign}).plot.bar(title="Benign versus attack distribution"); plt.tight_layout(); plt.savefig(report_dir / "benign_attack_distribution.png", dpi=160); plt.close()
    numeric.hist(figsize=(10, 7), bins=40)
    plt.suptitle("Model-feature distributions (finite values)")
    plt.tight_layout()
    plt.savefig(report_dir / "feature_distributions.png", dpi=160)
    plt.close()
    corr = numeric.corr(); plt.figure(figsize=(7,6)); plt.imshow(corr, cmap="coolwarm", vmin=-1, vmax=1); plt.xticks(range(len(corr)), corr.columns, rotation=75, ha="right"); plt.yticks(range(len(corr)), corr.columns); plt.colorbar(); plt.title("Model feature correlation"); plt.tight_layout(); plt.savefig(report_dir / "feature_correlation.png", dpi=160); plt.close()
    return summary

def main() -> None:
    parser=argparse.ArgumentParser(); parser.add_argument("path", type=Path); parser.add_argument("--report-dir", type=Path, default=Path("ml/reports")); parser.add_argument("--max-rows", type=int); args=parser.parse_args(); print(json.dumps(analyse(args.path,args.report_dir,args.max_rows), indent=2))
if __name__ == "__main__": main()
