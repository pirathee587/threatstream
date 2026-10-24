# ML model evaluation

## Status

No model has been trained or evaluated in this checkout because no local CIC-IDS2017 CSV data is available and Python ML dependencies are not installed. No accuracy, precision, recall, F1, confidence calibration, or production-detection claim is made.

## Reproducible evaluation

```bash
PYTHONPATH=ml/src python -m threatstream_ml.training dataset-processor/data/CIC-IDS2017
```

The command uses seed `202518`, validates the five feature columns, removes labels with no value, and makes stratified 60/20/20 train/validation/test splits. Logistic Regression and class-weighted Random Forest are selected by validation macro F1. The test split is untouched until the selected model is fit on train+validation data and evaluated once. Metrics include accuracy; macro and weighted precision, recall, and F1; per-class classification report; confusion matrix; training time; and held-out inference timing. They are saved to ignored `ml/artifacts/cicids2017-v1/metrics.json`; metadata, exact ordered feature schema, label classes, model checksum, and version are saved alongside it.

This row-wise split is reproducible but may leak capture-specific or duplicate flow patterns across splits. The saved metrics must be treated as offline CIC estimates, not real-world detection performance. A future capture/group-aware evaluation is required when stable capture identifiers are available.
