# Phase 20 — ML environment and dataset readiness

## Status: blocked; user-admin action and dataset are required

No Java service, frontend, Kafka contract, model artifact, or dataset file was changed in this phase. The environment and dataset checks below are read-only. No dataset was downloaded.

## Environment audit

| Check | Verified result |
| --- | --- |
| Host OS | Ubuntu 24.04.5 LTS (Noble), x86_64 |
| Python | `/usr/bin/python3`, Python 3.12.3 |
| Package manager | `apt` / `apt-get` available |
| `pip` / `ensurepip` | Both unavailable from the system interpreter |
| Virtual-environment support | `python3-venv` and `python3.12-venv` are not installed; `/usr/lib/python3.12/ensurepip` is absent |
| Available package | `python3.12-venv` candidate `3.12.3-1ubuntu0.17` is available through configured Ubuntu repositories |
| Free space | 47 GB on the workspace filesystem |
| Existing ML dependencies | pandas, NumPy, scikit-learn, joblib, matplotlib, confluent-kafka, and pytest are all absent from system Python |
| Isolated environment | No `ml/.venv` exists; it is ignored by Git once created |

The missing Ubuntu `python3.12-venv` package explains both the missing `ensurepip` module and the failed `python3 -m venv` command. This is an OS-managed dependency and requires an administrator; it was not installed by this phase.

## Required user action: isolated Python tooling

Run the following in a terminal with administrator access:

```bash
sudo apt update
sudo apt install --no-install-recommends python3.12-venv
```

Then, from the repository root, create and populate the isolated environment only:

```bash
python3 -m venv ml/.venv
ml/.venv/bin/python -m pip install -r ml/requirements.txt
ml/.venv/bin/python -m pytest ml/tests
```

This does not install Python packages into the system interpreter and preserves all unrelated project environments. `python3-pip` is not required for this workflow because `python3.12-venv` supplies pip within the virtual environment.

## Dataset readiness

The checked location `dataset-processor/data/CIC-IDS2017/` contains zero files; no CSV, Parquet, or PCAP exists elsewhere in the repository. The location is Git-ignored, as required for a large local dataset.

After reviewing the release terms, obtain the labeled CSV archive `MachineLearningCSV.zip` from the official [CIC-IDS2017 dataset page](https://www.unb.ca/cic/datasets/ids-2017.html). The source distinguishes the machine-learning CSV release from the raw PCAP data; this project needs the former. Do not substitute synthetic or unlabeled files.

Extract its `MachineLearningCVE/` directory below `dataset-processor/data/CIC-IDS2017/`. Retain these eight CSVs for the intended full multiclass evaluation:

- `Monday-WorkingHours.pcap_ISCX.csv`
- `Tuesday-WorkingHours.pcap_ISCX.csv`
- `Wednesday-workingHours.pcap_ISCX.csv`
- `Thursday-WorkingHours-Morning-WebAttacks.pcap_ISCX.csv`
- `Thursday-WorkingHours-Afternoon-Infilteration.pcap_ISCX.csv`
- `Friday-WorkingHours-Morning.pcap_ISCX.csv`
- `Friday-WorkingHours-Afternoon-PortScan.pcap_ISCX.csv`
- `Friday-WorkingHours-Afternoon-DDos.pcap_ISCX.csv`

Verify the local extraction without loading the full dataset into memory:

```bash
find dataset-processor/data/CIC-IDS2017 -type f -name '*.csv' -printf '%f\t%s bytes\n' | sort
ml/.venv/bin/python - <<'PY'
from pathlib import Path
import pandas as pd

root = Path('dataset-processor/data/CIC-IDS2017')
for csv_file in sorted(root.rglob('*.csv')):
    header = pd.read_csv(csv_file, nrows=0)
    normalized = {''.join(str(column).strip().lower().split()): str(column) for column in header.columns}
    required = {'label', 'flowduration', 'totalfwdpackets', 'totalbackwardpackets', 'flowbytes/s', 'flowpackets/s'}
    missing = sorted(required - normalized.keys())
    if missing:
        print(csv_file.name, f'MISSING: {missing}')
        continue
    label_column = normalized['label']
    labels = pd.read_csv(csv_file, usecols=[label_column], nrows=10_000)[label_column].astype('string').str.strip()
    print(csv_file.name, 'OK', 'sample-labels=', labels.value_counts().head(10).to_dict())
PY
```

## Commands executed in this phase

| Command/check | Outcome |
| --- | --- |
| Read Phase 19 report, ML README, and requirements | Completed. |
| OS, interpreter, package-manager, package-state, dependency, and disk audit | Completed; findings above. |
| `python3 -m venv /tmp/threatstream-phase19-venv` (Phase 19 evidence reviewed) | Blocked by absent `ensurepip`. |
| Current dataset/file/header inspection | Completed; no files available to inspect. |
| `apt-cache policy python3.12-venv` | Candidate package is available; no install attempted. |

## Deferred validation

Because no dependency environment or real CIC CSV exists, this phase did not run analysis, pytest, training, artifact checksum validation against a real model, inference, or Kafka publishing. No accuracy, class distribution, labels, artifact, or integration result is claimed.

After the two prerequisites are supplied, run in order:

```bash
PYTHONPATH=ml/src ml/.venv/bin/python -m threatstream_ml.analysis dataset-processor/data/CIC-IDS2017
PYTHONPATH=ml/src ml/.venv/bin/python -m pytest ml/tests
PYTHONPATH=ml/src ml/.venv/bin/python -m threatstream_ml.training dataset-processor/data/CIC-IDS2017
```

Only after training succeeds should the saved artifact be loaded through `Predictor` and an isolated local Kafka test be performed.
