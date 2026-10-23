# CIC-IDS2017 dataset processor

The processor reads CIC-IDS2017 CSV files in chunks and emits sanitized `NETWORK_FLOW` schema-v1 events to `security.network.events`. Obtain the dataset from the [Canadian Institute for Cybersecurity CIC-IDS2017 page](https://www.unb.ca/cic/datasets/ids-2017.html), then place selected CSV files below `data/CIC-IDS2017/` (ignored by Git).

## Setup and commands

```bash
python3 -m venv .venv
source .venv/bin/activate                 # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
PYTHONPATH=src pytest

# Validate one CSV without Kafka publishing
PYTHONPATH=src python -m threatstream_processor.replay data/CIC-IDS2017/Monday.csv --dry-run --chunk-size 5000

# Replay a small sample at 20 events/sec
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 PYTHONPATH=src python -m threatstream_processor.replay data/CIC-IDS2017/Monday.csv --max-events 100 --rate 20

# Replay every CSV under a selected directory
PYTHONPATH=src python -m threatstream_processor.replay data/CIC-IDS2017 --chunk-size 10000
```

For Docker-network execution use `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`; host execution uses the loopback-only listener `localhost:9094`. The processor uses `confluent-kafka`, waits for delivery callbacks on flush, and reports delivery failure rather than successful replay.

## Schema and data policy

Events contain `eventId`, `eventType`, `schemaVersion`, UTC `timestamp`, source/destination IP and ports, protocol, available validated flow measurements, and `serviceName`. Required flow identifiers, timestamp, ports, and invalid/non-finite mapped measurements cause a record to be skipped and counted. Columns are normalized safely and reported in the summary; CSV parser-malformed rows are counted.

`Label` is strictly evaluation metadata. It appears only in the local end-of-run label distribution and is never included in event dictionaries, Kafka values, keys, or headers. This intentionally prevents the detection pipeline from learning ground truth.
