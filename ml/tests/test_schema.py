from threatstream_ml.schema import EventValidationError, validate_network_event

VALID = {"eventId":"e-1","eventType":"NETWORK_FLOW","schemaVersion":1,"timestamp":"2026-01-01T00:00:00Z","sourceIp":"10.0.0.1","destinationIp":"10.0.0.2","sourcePort":1000,"destinationPort":443,"protocol":"6","flowDuration":4,"totalForwardPackets":2,"totalBackwardPackets":3,"flowBytesPerSecond":5.5,"flowPacketsPerSecond":1.2,"correlationId":"c-1"}

def test_valid_event_preserves_identifiers():
    event=validate_network_event(VALID)
    assert event["eventId"] == "e-1" and event["correlationId"] == "c-1"

def test_missing_model_feature_is_rejected_not_imputed():
    event=dict(VALID); event.pop("flowBytesPerSecond")
    try: validate_network_event(event)
    except EventValidationError as error: assert "missing required model feature" in str(error)
    else: raise AssertionError("missing feature should be rejected")

def test_infinite_and_bad_ports_are_rejected():
    event=dict(VALID, flowPacketsPerSecond=float("inf"))
    try: validate_network_event(event)
    except EventValidationError: pass
    else: raise AssertionError("infinity should be rejected")
    event=dict(VALID, sourcePort=70000)
    try: validate_network_event(event)
    except EventValidationError: pass
    else: raise AssertionError("invalid port should be rejected")
