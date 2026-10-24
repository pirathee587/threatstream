# ML dataset analysis

## Status

The reproducible analysis implementation is complete, but no CIC-IDS2017 CSV files are present in this checkout. Therefore this document intentionally contains no dataset size, class distribution, visualizations, or data-quality counts.

## Procedure

Obtain CIC-IDS2017 from the Canadian Institute for Cybersecurity, place CSVs under `dataset-processor/data/CIC-IDS2017/` (an ignored location), install `ml/requirements.txt`, then run:

```bash
PYTHONPATH=ml/src python -m threatstream_ml.analysis dataset-processor/data/CIC-IDS2017
```

The command writes ignored, reproducible outputs to `ml/reports/`: `dataset_summary.json`, attack-category and benign/attack charts, a five-feature distribution chart, and the model-feature correlation heatmap. It reports columns/types, duplicate rows, missing values, labels, and the usable labeled row count. It normalizes CIC header spelling and treats non-finite feature values as missing for later train-only imputation.

Only these replay-compatible measurements are candidates for the model: flow duration, total forward/backward packets, flow bytes/s, and flow packets/s. Flow IDs, addresses, ports, timestamps, and labels are excluded from model features to avoid identifier/target leakage.
