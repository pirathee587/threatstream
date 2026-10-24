# ML detection service guide

The Python service is an optional, independent Kafka consumer. It loads a locally trained, checksum-verified artifact once at startup; it never trains from event traffic.

## Phase 19 verification status

No artifact exists in this checkout because the CIC CSVs and isolated Python dependencies are unavailable. Consequently startup/model loading, real inference, and local Kafka publishing have not been claimed as verified. Unit tests exercise artifact checksum/schema validation and normalized prediction with a test double; run the commands below after creating the artifact to verify the real model.

## Topics and contracts

It consumes `security.network.events` with group `ml-detection-service-v1`, independent of the Java consumer group, so it cannot steal Java rule-engine records. Input must be a `NETWORK_FLOW` schema v1 event emitted by the existing replay processor and include all five numeric flow features. Missing, non-finite, or incompatible model features are rejected to `security.events.dlt`; they are never invented or silently defaulted.

It publishes `ML_DETECTION_RESULT` schema v1 to `security.ml.alerts` with required `eventId`, `timestamp`, `prediction` (`BENIGN`/`ATTACK`), `attackType`, `modelVersion`, and `evidenceSummary`; `sourceIp`, calibrated model-class confidence, and `correlationId` are included when available. Confidence is the classifier's highest class probability, not a risk score. No `riskScore` is emitted.

The service uses Kafka `acks=all`, idempotent producer mode, three producer retries, synchronous bounded delivery waits, a bounded in-memory event-ID duplicate cache, and graceful consumer close. Permanent malformed input goes to the existing DLT. Transient failure leaves the input uncommitted for Kafka redelivery.

## Run

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9094 \
ML_MODEL_DIR=ml/artifacts/cicids2017-v1 \
PYTHONPATH=ml/src python -m threatstream_ml.service
```

Before starting the service, validate the artifact with `PYTHONPATH=ml/src pytest ml/tests` and ensure `ML_MODEL_DIR` contains the `model.joblib`, `metadata.json`, and `metrics.json` emitted by the training command. The loader verifies the saved SHA-256 and exact five-column feature schema before deserializing the model.

Environment variables: `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC_NETWORK_EVENTS`, `KAFKA_TOPIC_ML_ALERTS`, `KAFKA_TOPIC_DLT`, `KAFKA_ML_GROUP_ID`, `ML_MODEL_DIR`, `ML_DEDUP_CACHE_SIZE`, `ML_PROCESSING_RETRY_ATTEMPTS`, and `ML_LOG_LEVEL`. The service logs IDs and operational errors only; it does not log raw traffic payloads or credentials.

## Existing alert path

The inspected Alert Service deserializes only `SECURITY_ALERT` from `security.alerts`; it does not currently consume `security.ml.alerts`. To avoid a breaking contract change, Phase 18 does not claim ML results reach MongoDB, REST, or the dashboard. A future backward-compatible Java bridge should consume `security.ml.alerts`, correlate/deduplicate against rule alerts by `eventId`/correlation ID, assign a separately calibrated risk policy, and publish a standard `SECURITY_ALERT` to `security.alerts`.
