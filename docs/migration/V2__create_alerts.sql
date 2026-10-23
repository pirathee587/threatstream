CREATE TABLE alerts (
  id BIGSERIAL PRIMARY KEY,
  alert_id UUID NOT NULL UNIQUE,
  event_type VARCHAR(64) NOT NULL,
  schema_version INTEGER NOT NULL,
  rule_id VARCHAR(128) NOT NULL,
  threat_type VARCHAR(128) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  source_ip VARCHAR(64) NOT NULL,
  description VARCHAR(1024) NOT NULL,
  evidence JSONB NOT NULL,
  first_seen TIMESTAMPTZ NOT NULL,
  last_seen TIMESTAMPTZ NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT alerts_severity_check CHECK (severity IN ('MEDIUM','HIGH','CRITICAL')),
  CONSTRAINT alerts_status_check CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED'))
);
CREATE INDEX idx_alerts_severity ON alerts(severity);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_threat_type ON alerts(threat_type);
CREATE INDEX idx_alerts_source_ip ON alerts(source_ip);
CREATE INDEX idx_alerts_detected_at ON alerts(detected_at DESC);
