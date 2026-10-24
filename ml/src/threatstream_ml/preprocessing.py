"""Shared, train-only fitted preprocessing and CIC column normalization."""
from __future__ import annotations
import re
from typing import Iterable
import numpy as np
import pandas as pd
from .schema import FEATURE_COLUMNS

LABEL_CANDIDATES = ("label",)
_ALIASES = {
    "flowDuration": ("flowduration",), "totalForwardPackets": ("totalforwardpackets", "totfwdpkts"),
    "totalBackwardPackets": ("totalbackwardpackets", "totbwdpkts"),
    "flowBytesPerSecond": ("flowbytes/s", "flowbytespersecond"),
    "flowPacketsPerSecond": ("flowpackets/s", "flowpacketspersecond"),
}

def normalized_name(name: str) -> str:
    return re.sub(r"\s+", "", str(name).strip().lower())

def resolve_feature_columns(columns: Iterable[str]) -> dict[str, str]:
    available = {normalized_name(column): str(column) for column in columns}
    mapping = {target: next((available[x] for x in aliases if x in available), None) for target, aliases in _ALIASES.items()}
    missing = [target for target, source in mapping.items() if source is None]
    if missing:
        raise ValueError("dataset does not contain required features: " + ", ".join(missing))
    return mapping

def resolve_label_column(columns: Iterable[str]) -> str:
    available = {normalized_name(column): str(column) for column in columns}
    for candidate in LABEL_CANDIDATES:
        if candidate in available:
            return available[candidate]
    raise ValueError("dataset does not contain a Label column")

def prepare_frame(frame: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    mapping = resolve_feature_columns(frame.columns)
    label_column = resolve_label_column(frame.columns)
    features = pd.DataFrame({target: pd.to_numeric(frame[source], errors="coerce") for target, source in mapping.items()})
    features = features.replace([np.inf, -np.inf], np.nan)
    labels = frame[label_column].astype("string").str.strip()
    valid_labels = labels.notna() & labels.ne("")
    return features.loc[valid_labels, list(FEATURE_COLUMNS)], labels.loc[valid_labels]

def labels_to_attack_binary(labels: pd.Series) -> pd.Series:
    return labels.str.casefold().ne("benign").map({False: "BENIGN", True: "ATTACK"})
