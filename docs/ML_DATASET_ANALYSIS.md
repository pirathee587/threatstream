# ML dataset analysis

## Status

Phase 19 verified that no CIC-IDS2017 CSV, parquet, or PCAP file is present in this checkout. Therefore this document intentionally contains no dataset size, class distribution, visualizations, or data-quality counts.

## Procedure

Download `MachineLearningCSV.zip` from the official [CIC-IDS2017 dataset page](https://www.unb.ca/cic/datasets/ids-2017.html), not the PCAP-only download. Extract the CSVs into `dataset-processor/data/CIC-IDS2017/` (an ignored location). For a full multiclass run, retain the eight CSVs normally distributed as `MachineLearningCVE/`:

- `Monday-WorkingHours.pcap_ISCX.csv`
- `Tuesday-WorkingHours.pcap_ISCX.csv`
- `Wednesday-workingHours.pcap_ISCX.csv`
- `Thursday-WorkingHours-Morning-WebAttacks.pcap_ISCX.csv`
- `Thursday-WorkingHours-Afternoon-Infilteration.pcap_ISCX.csv`
- `Friday-WorkingHours-Morning.pcap_ISCX.csv`
- `Friday-WorkingHours-Afternoon-PortScan.pcap_ISCX.csv`
- `Friday-WorkingHours-Afternoon-DDos.pcap_ISCX.csv`

The pipeline recursively reads CSVs, so the extracted directory layout is accepted. Each selected CSV must include `Label`, `Flow Duration`, `Total Fwd Packets`, `Total Backward Packets`, `Flow Bytes/s`, and `Flow Packets/s` (normalized spelling aliases are supported). Install `ml/requirements.txt` in an isolated virtual environment, then run:

```bash
PYTHONPATH=ml/src python -m threatstream_ml.analysis dataset-processor/data/CIC-IDS2017
```

The command writes ignored, reproducible outputs to `ml/reports/`: `dataset_summary.json`, attack-category and benign/attack charts, a five-feature distribution chart, and the model-feature correlation heatmap. It reports columns/types, duplicate rows, raw infinite-value count, missing values, labels, and the usable labeled row count. It normalizes CIC header spelling and treats non-finite feature values as missing for later train-only imputation.

Only these replay-compatible measurements are candidates for the model: flow duration, total forward/backward packets, flow bytes/s, and flow packets/s. Flow IDs, addresses, ports, timestamps, and labels are excluded from model features to avoid identifier/target leakage.
