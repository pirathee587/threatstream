import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { changeStatus, getAlert, getApiErrorMessage } from '@/services/alertApi';
import type { Alert } from '@/types/alerts';

function formatDate(isoString: string | undefined): string {
  if (!isoString) return 'Not recorded';
  try {
    const d = new Date(isoString);
    return isNaN(d.getTime()) ? isoString : d.toLocaleString();
  } catch {
    return isoString;
  }
}

export function AlertDetailPage() {
  const { alertId = '' } = useParams<{ alertId: string }>();
  const [alert, setAlert] = useState<Alert>();
  const [error, setError] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [action, setAction] = useState<'acknowledge' | 'resolve'>();
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    if (!alertId) return;
    try {
      const data = await getAlert(alertId);
      setAlert(data);
      setError(undefined);
    } catch (cause) {
      setError(getApiErrorMessage(cause, 'Unable to load alert details.'));
    }
  }, [alertId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function updateStatus(next: 'acknowledge' | 'resolve') {
    if (!alertId || action) return;
    setAction(next);
    setActionError(undefined);
    try {
      const updated = await changeStatus(alertId, next);
      setAlert(updated);
    } catch (cause) {
      setActionError(getApiErrorMessage(cause, `Unable to ${next} alert.`));
    } finally {
      setAction(undefined);
    }
  }

  function copyEvidence() {
    if (!alert?.evidence) return;
    navigator.clipboard?.writeText(JSON.stringify(alert.evidence, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  if (error && !alert) {
    return (
      <section className="detail">
        <Link to="/alerts" className="detail-back">
          ← Alerts
        </Link>
        <div className="notice error" role="alert">
          {error}
        </div>
      </section>
    );
  }

  if (!alert) {
    return (
      <section className="detail">
        <Link to="/alerts" className="detail-back">
          ← Alerts
        </Link>
        <p>Loading alert…</p>
      </section>
    );
  }

  return (
    <section className="detail">
      <Link to="/alerts" className="detail-back">
        ← Alerts
      </Link>

      <div className="page-heading">
        <div>
          <h1>{alert.threatType}</h1>
          <p>
            Alert ID: <code>{alert.alertId}</code>
          </p>
        </div>
        <span className={`badge ${alert.severity.toLowerCase()}`}>{alert.severity}</span>
      </div>

      {actionError && (
        <div className="notice error" role="alert">
          {actionError}
        </div>
      )}

      <p className="description">{alert.description}</p>

      <dl>
        <dt>Alert ID</dt>
        <dd>
          <code>{alert.alertId}</code>
        </dd>

        <dt>Rule ID</dt>
        <dd>
          <code>{alert.ruleId}</code>
        </dd>

        <dt>Severity</dt>
        <dd>
          <span className={`badge ${alert.severity.toLowerCase()}`}>{alert.severity}</span>
        </dd>

        <dt>Status</dt>
        <dd>
          <span className={`status-badge status-${alert.status.toLowerCase()}`}>
            {alert.status}
          </span>
        </dd>

        <dt>Source IP</dt>
        <dd>
          <code>{alert.sourceIp || 'Not applicable'}</code>
        </dd>

        <dt>Detected</dt>
        <dd>{formatDate(alert.detectedAt)}</dd>

        <dt>First seen</dt>
        <dd>{formatDate(alert.firstSeen)}</dd>

        <dt>Last seen</dt>
        <dd>{formatDate(alert.lastSeen)}</dd>

        <dt>Created at</dt>
        <dd>{formatDate(alert.createdAt)}</dd>

        <dt>Updated at</dt>
        <dd>{formatDate(alert.updatedAt)}</dd>
      </dl>

      <div className="panel-heading" style={{ marginTop: '1.5rem' }}>
        <h2>Investigation Evidence</h2>
        {alert.evidence && Object.keys(alert.evidence).length > 0 && (
          <button
            type="button"
            className="btn-secondary"
            onClick={copyEvidence}
            style={{ fontSize: '0.8rem', padding: '0.35rem 0.65rem' }}
          >
            {copied ? 'Copied!' : 'Copy JSON'}
          </button>
        )}
      </div>

      <pre>
        {alert.evidence && Object.keys(alert.evidence).length > 0
          ? JSON.stringify(alert.evidence, null, 2)
          : '// No additional evidence payload attached to this alert'}
      </pre>

      {alert.status !== 'RESOLVED' ? (
        <div className="actions">
          {alert.status === 'OPEN' && (
            <button
              type="button"
              className="btn-secondary"
              disabled={!!action}
              onClick={() => void updateStatus('acknowledge')}
            >
              {action === 'acknowledge' ? 'Acknowledging…' : 'Acknowledge'}
            </button>
          )}
          <button
            type="button"
            className="btn-success"
            disabled={!!action}
            onClick={() => void updateStatus('resolve')}
          >
            {action === 'resolve' ? 'Resolving…' : 'Resolve'}
          </button>
        </div>
      ) : (
        <div className="notice info" style={{ marginTop: '1.5rem' }}>
          <span>
            <strong>Resolution locked:</strong> This alert has been marked RESOLVED. Under Alert
            Service business rules, resolved alerts are terminal and cannot be reopened.
          </span>
        </div>
      )}
    </section>
  );
}
