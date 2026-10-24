"""Stable model and Kafka contracts; no labels are accepted from Kafka."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import math
from typing import Any

FEATURE_COLUMNS = (
    "flowDuration", "totalForwardPackets", "totalBackwardPackets",
    "flowBytesPerSecond", "flowPacketsPerSecond",
)
REQUIRED_EVENT_FIELDS = ("eventId", "eventType", "schemaVersion", "timestamp", "sourceIp", "destinationIp", "sourcePort", "destinationPort", "protocol")


class EventValidationError(ValueError):
    """A permanent, non-retryable network-event contract violation."""


def _finite_number(value: Any, field: str) -> float:
    if isinstance(value, bool):
        raise EventValidationError(f"{field} must be numeric")
    try:
        parsed = float(value)
    except (TypeError, ValueError) as error:
        raise EventValidationError(f"{field} must be numeric") from error
    if not math.isfinite(parsed) or parsed < 0:
        raise EventValidationError(f"{field} must be finite and non-negative")
    return parsed


def validate_network_event(event: Any) -> dict[str, Any]:
    if not isinstance(event, dict):
        raise EventValidationError("event must be an object")
    missing = [field for field in REQUIRED_EVENT_FIELDS if event.get(field) in (None, "")]
    if missing:
        raise EventValidationError("missing required fields: " + ", ".join(missing))
    if event["eventType"] != "NETWORK_FLOW" or event["schemaVersion"] != 1:
        raise EventValidationError("unsupported network event type or schema version")
    try:
        datetime.fromisoformat(str(event["timestamp"]).replace("Z", "+00:00"))
    except ValueError as error:
        raise EventValidationError("timestamp must be ISO-8601") from error
    for port in ("sourcePort", "destinationPort"):
        value = event[port]
        if isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 65535:
            raise EventValidationError(f"{port} must be an integer in [0, 65535]")
    normalized = {key: event[key] for key in REQUIRED_EVENT_FIELDS}
    for column in FEATURE_COLUMNS:
        if column not in event:
            raise EventValidationError(f"missing required model feature: {column}")
        normalized[column] = _finite_number(event[column], column)
    correlation_id = event.get("correlationId")
    if correlation_id is not None:
        normalized["correlationId"] = str(correlation_id)
    return normalized


@dataclass(frozen=True)
class MlResult:
    event_id: str
    timestamp: str
    source_ip: str
    prediction: str
    attack_type: str
    model_version: str
    confidence: float | None
    correlation_id: str | None

    def as_dict(self) -> dict[str, Any]:
        result = {
            "eventId": self.event_id, "eventType": "ML_DETECTION_RESULT", "schemaVersion": 1,
            "timestamp": self.timestamp, "sourceIp": self.source_ip, "prediction": self.prediction,
            "attackType": self.attack_type, "modelVersion": self.model_version,
            "evidenceSummary": "CIC flow model prediction based on five validated flow measurements.",
        }
        if self.confidence is not None:
            result["confidence"] = self.confidence
        if self.correlation_id:
            result["correlationId"] = self.correlation_id
        return result
