# ThreatStream ML detection service

This optional Python service classifies validated CIC-IDS2017 flow measurements. It consumes a distinct Kafka consumer group and publishes versioned `ML_DETECTION_RESULT` messages to `security.ml.alerts`; it does not train in production and does not modify Java rule detections.

Install dependencies in an isolated environment, put CIC CSV files below `dataset-processor/data/CIC-IDS2017/`, then run:

```bash
PYTHONPATH=ml/src python -m threatstream_ml.analysis dataset-processor/data/CIC-IDS2017
PYTHONPATH=ml/src python -m threatstream_ml.training dataset-processor/data/CIC-IDS2017
PYTHONPATH=ml/src python -m threatstream_ml.service
PYTHONPATH=ml/src pytest ml/tests
```

The analysis and training commands write ignored reports/artifacts under `ml/`. Training refuses a one-class dataset and writes metrics only after a held-out test evaluation. See `docs/ML_SERVICE_GUIDE.md` for input/output contracts.
