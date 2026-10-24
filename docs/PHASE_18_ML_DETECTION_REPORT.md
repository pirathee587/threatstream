# Phase 18 — ML detection audit and implementation report

## Repository audit

The repository has Java 21 Spring services, Kafka 3.9 KRaft topic initialization, and a Python `dataset-processor`. Its CIC replay maps and validates five numerical flow fields into `NETWORK_FLOW` schema v1 events on `security.network.events`, deliberately omitting the source `Label`. The Java network engine consumes that topic using its own consumer group, performs rule detection and deduplication, and publishes `SECURITY_ALERT` events to `security.alerts`. The Alert Service validates/persists only that alert contract. Both Java paths have bounded retry and `security.events.dlt` configuration; correlation headers are supported. Existing Phase 12–16 documentation establishes bounded retries, no production-data integration tests, and verified-but-environment-limited reporting conventions.

Docker topic initialization has been extended with `security.ml.alerts`. No service port, existing Java event contract, frontend code, database, deployment infrastructure, or Java detection behavior was changed. Python 3.12.3 is present; this host has no installed pandas, NumPy, scikit-learn, matplotlib, joblib, confluent-kafka, or pytest, and the local CIC dataset directory is absent.

## Implemented

`ml/` contains reproducible analysis/training commands, common preprocessing, versioned/checksum-verified artifacts, strict event validation, and an independent-group Kafka inference service. Tests cover malformed/missing/infinite input, fixed feature ordering, unexpected columns, label handling, artifact checksum/schema validation, normalized prediction, confidence, and correlation preservation. The service makes no uncalibrated risk-score claim.

## Verification and status

Implementation-level tests have not run on this host because the required Python dependencies are unavailable. Dataset analysis, model training/evaluation, generated reports, Kafka runtime verification, Java alert bridge, and dashboard/API appearance remain pending local dataset/dependency availability. Consequently no ML metrics, dataset distribution, throughput, or end-to-end alert claim is reported.

See `ML_DATASET_ANALYSIS.md`, `ML_MODEL_EVALUATION.md`, and `ML_SERVICE_GUIDE.md` for reproducible commands, contracts, and limitations.
