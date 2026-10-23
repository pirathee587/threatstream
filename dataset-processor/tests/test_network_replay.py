import pandas as pd
from threatstream_processor.replay import column_mapping, event_from_row

def row(**overrides):
    data={" Source IP":"10.0.0.1"," Destination IP":"10.0.0.2"," Source Port":"123"," Destination Port":"443"," Protocol":"6"," Timestamp":"2017-07-03 12:00:00"," Flow Duration":"10"," Label":"BENIGN"};data.update(overrides);return pd.Series(data)
def test_normalization_and_label_separation():
    value=row();event=event_from_row(value,column_mapping(value.index))
    assert event["eventType"]=="NETWORK_FLOW"
    assert "label" not in {key.lower() for key in event}
    assert "BENIGN" not in str(event)
def test_invalid_port_and_timestamp_are_skipped():
    value=row(**{" Source Port":"70000"});assert event_from_row(value,column_mapping(value.index)) is None
    value=row(**{" Timestamp":"not-a-time"});assert event_from_row(value,column_mapping(value.index)) is None
def test_unique_event_identifiers():
    value=row();mapping=column_mapping(value.index);assert event_from_row(value,mapping)["eventId"]!=event_from_row(value,mapping)["eventId"]
