# Phase 19 — CIC-IDS2017 preparation, training, and evaluation

## Status: blocked by local prerequisites

Phase 19 cannot train or evaluate a real model in this checkout. The audit found zero CIC-IDS2017 CSV, Parquet, or PCAP files under `dataset-processor/data/CIC-IDS2017/` and the rest of the project. Python is `3.12.3`, but system Python has neither `pip` nor `ensurepip`; creating an isolated virtual environment with `python3 -m venv /tmp/threatstream-phase19-venv` failed because the `python3.12-venv` package is absent. No dependencies were installed, no dataset was downloaded, and no fabricated records or metrics were used.

## Audit and implementation checks

The Phase 18 module was reviewed end-to-end: its fixed feature schema is `flowDuration`, `totalForwardPackets`, `totalBackwardPackets`, `flowBytesPerSecond`, and `flowPacketsPerSecond`; identifiers, addresses, ports, timestamps, and labels are excluded from model inputs. The train pipeline fits median imputation/scaling only within scikit-learn training pipelines, holds out a stratified 20% test split, uses the remaining data for 60/20 train/validation selection, and compares class-weighted Logistic Regression and Random Forest by validation macro F1. The selected model is refit on train+validation only, then evaluated exactly once on the held-out test set.

Artifact metadata records format version, ordered feature schema, model version, UTC creation time, label classes, metrics, and SHA-256. The inference loader checks format/schema/checksum before `joblib` deserialization. The service validates every required event and model feature, preserves IDs/correlation IDs, uses its independent `ml-detection-service-v1` consumer group, and sends results to `security.ml.alerts`; it does not change Java consumers, `security.alerts`, or the frontend.

Two audited implementation corrections were made in this phase: analysis now counts raw infinite feature values before replacement, and training output now includes top-level macro and weighted precision, recall, and F1 in addition to accuracy, classification reports, confusion matrix, and timings.

## Commands attempted and results

| Command | Result |
| --- | --- |
| Dataset search for CSV/Parquet/PCAP | Passed; zero files found. |
| `python3 -m venv /tmp/threatstream-phase19-venv` | Blocked: `ensurepip` unavailable; install the OS `python3.12-venv` package. |
| `PYTHONPATH=ml/src python3 -m pytest ml/tests` | Blocked: `pytest` is not installed. |
| `PYTHONPATH=ml/src python3 -m threatstream_ml.analysis dataset-processor/data/CIC-IDS2017 --max-rows 1` | Blocked before dataset access: `matplotlib` is not installed. |
| `python3 -m compileall -q ml/src ml/tests` | Passed. |

## Required next steps

1. Install the OS package that supplies `ensurepip` (for this host, `python3.12-venv`) and create a repository-local ignored environment, for example `python3 -m venv ml/.venv`.
2. Activate it and run `python -m pip install -r ml/requirements.txt`.
3. Download the official CIC `MachineLearningCSV.zip` after reviewing its terms; do not download it automatically from this phase. Extract the eight listed CSVs under `dataset-processor/data/CIC-IDS2017/`.
4. Run `PYTHONPATH=ml/src python -m threatstream_ml.analysis dataset-processor/data/CIC-IDS2017`, then `PYTHONPATH=ml/src python -m threatstream_ml.training dataset-processor/data/CIC-IDS2017`.
5. Run `PYTHONPATH=ml/src pytest ml/tests`, load the resulting artifact through `Predictor`, and only then perform an isolated local Kafka test against `security.network.events` and `security.ml.alerts`.

The official [CIC-IDS2017 page](https://www.unb.ca/cic/datasets/ids-2017.html) describes its labeled CICFlowMeter CSV release as `MachineLearningCSV.zip`, separate from the PCAP data, and identifies the covered benign and attack traffic. Offline dataset metrics must not be presented as production detection performance.
