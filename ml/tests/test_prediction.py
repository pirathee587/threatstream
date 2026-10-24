import numpy as np
from threatstream_ml.service import Predictor
from threatstream_ml.schema import FEATURE_COLUMNS
from test_schema import VALID

class Model:
    def predict(self, frame):
        assert list(frame.columns) == list(FEATURE_COLUMNS)
        return np.array(["DoS Hulk"])
    def predict_proba(self, frame): return np.array([[.1,.9]])

def test_prediction_is_normalized_and_preserves_correlation_without_risk_score():
    predictor=Predictor.__new__(Predictor); predictor.model=Model(); predictor.metadata={"modelVersion":"test-v1"}
    result=predictor.predict(VALID)
    assert result["prediction"] == "ATTACK"
    assert result["attackType"] == "DoS Hulk"
    assert result["confidence"] == .9
    assert result["correlationId"] == "c-1"
    assert "riskScore" not in result
