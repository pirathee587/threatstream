"""Versioned artifact loading with explicit local ownership checks."""
from __future__ import annotations
from pathlib import Path
import hashlib, json
import joblib
from . import MODEL_FORMAT_VERSION
from .schema import FEATURE_COLUMNS

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def load_artifact(artifact_dir: Path) -> tuple[object, dict]:
    artifact_dir = artifact_dir.resolve()
    model_path, metadata_path = artifact_dir / "model.joblib", artifact_dir / "metadata.json"
    if not model_path.is_file() or not metadata_path.is_file():
        raise RuntimeError(f"model artifact is incomplete in {artifact_dir}")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if metadata.get("formatVersion") != MODEL_FORMAT_VERSION or metadata.get("featureColumns") != list(FEATURE_COLUMNS):
        raise RuntimeError("model artifact metadata has an unsupported schema")
    if metadata.get("modelSha256") != sha256(model_path):
        raise RuntimeError("model artifact checksum mismatch; only load locally trained artifacts")
    # joblib/pickle is unsafe for untrusted files. The checksum only establishes integrity
    # against the metadata written by this local training command; artifact location is operator-owned.
    return joblib.load(model_path), metadata
