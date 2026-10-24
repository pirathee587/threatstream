import numpy as np
import pandas as pd
import pytest
from threatstream_ml.preprocessing import prepare_frame
from threatstream_ml.schema import FEATURE_COLUMNS

def _frame():
    return pd.DataFrame({"Flow Duration":[1,2,3],"Total Fwd Packets":[1,2,3],"Total Backward Packets":[1,2,3],"Flow Bytes/s":[1.0,np.inf,3.0],"Flow Packets/s":[1.0,2.0,3.0],"Label":["BENIGN","DoS","BENIGN"],"Flow ID":["leak-a","leak-b","leak-c"]})

def test_preparation_uses_explicit_features_in_fixed_order_and_removes_infinity():
    features,labels=prepare_frame(_frame())
    assert tuple(features.columns) == FEATURE_COLUMNS
    assert np.isnan(features.loc[1,"flowBytesPerSecond"])
    assert list(labels) == ["BENIGN","DoS","BENIGN"]

def test_unexpected_columns_do_not_become_features():
    features,_=prepare_frame(_frame())
    assert "Flow ID" not in features

def test_missing_required_column_fails_fast():
    with pytest.raises(ValueError,match="required features"):
        prepare_frame(_frame().drop(columns="Flow Duration"))
