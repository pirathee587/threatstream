import json
import joblib
import pytest
from threatstream_ml import MODEL_FORMAT_VERSION
from threatstream_ml.artifacts import load_artifact, sha256
from threatstream_ml.schema import FEATURE_COLUMNS

def test_loader_requires_matching_schema_and_checksum(tmp_path):
    model_path=tmp_path / "model.joblib"; joblib.dump({"known":"test artifact"},model_path)
    metadata={"formatVersion":MODEL_FORMAT_VERSION,"featureColumns":list(FEATURE_COLUMNS),"modelSha256":sha256(model_path),"modelVersion":"test-v1"}
    (tmp_path / "metadata.json").write_text(json.dumps(metadata))
    model,loaded=load_artifact(tmp_path)
    assert model["known"] == "test artifact" and loaded["modelVersion"] == "test-v1"
    metadata["modelSha256"]="bad"; (tmp_path / "metadata.json").write_text(json.dumps(metadata))
    with pytest.raises(RuntimeError,match="checksum"):
        load_artifact(tmp_path)
