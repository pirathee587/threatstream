import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Bar,
  BarChart,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Link } from 'react-router-dom';
import { getAlerts, getApiErrorMessage, getStatistics } from '@/services/alertApi';
import type { AlertPage, AlertStatistics } from '@/types/alerts';

const severityColors: Record<string, string> = {
  CRITICAL: '#ef4444',
  HIGH: '#f97316',
  MEDIUM: '#eab308',
};

const threatColors = ['#38bdf8', '#818cf8', '#f43f5e', '#34d399', '#fbbf24', '#a855f7'];

const defaultInterval = Number(import.meta.env.VITE_ALERT_POLL_INTERVAL_MS ?? 30000);

function Metric({
  label,
  value,
  variant,
}: {
  label: string;
  value: number | undefined;
  variant?: 'critical' | 'open' | 'resolved';
}) {
  const className = variant ? `metric metric-${variant}` : 'metric';
  return (
    <article className={className}>
      <span>{label}</span>
      <strong>{value !== undefined ? value.toLocaleString() : '—'}</strong>
    </article>
  );
}

export function AlertDashboard() {
  const [stats, setStats] = useState<AlertStatistics>();
  const [recent, setRecent] = useState<AlertPage>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [lastRefreshed, setLastRefreshed] = useState<Date>();
  const version = useRef(0);

  const refresh = useCallback(async () => {
    const current = ++version.current;
    setLoading(true);
    try {
      const [statistics, page] = await Promise.all([
        getStatistics(),
        getAlerts({ page: 0, size: 6, sortBy: 'detectedAt', direction: 'DESC' }),
      ]);
      if (current === version.current) {
        setStats(statistics);
        setRecent(page);
        setLastRefreshed(new Date());
        setError(undefined);
      }
    } catch (cause) {
      if (current === version.current) {
        setError(getApiErrorMessage(cause, 'Unable to load alert telemetry.'));
      }
    } finally {
      if (current === version.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void refresh();
    const pollInterval = Math.max(5000, defaultInterval);
    const timer = window.setInterval(() => {
      void refresh();
    }, pollInterval);

    return () => {
      window.clearInterval(timer);
      version.current += 1;
    };
  }, [refresh]);

  const severityData = Object.entries(stats?.bySeverity ?? {}).map(([name, value]) => ({
    name,
    value,
  }));

  const threatData = Object.entries(stats?.byThreatType ?? {}).map(([name, value]) => ({
    name,
    value,
  }));

  const highAndCritical = (stats?.bySeverity?.HIGH ?? 0) + (stats?.bySeverity?.CRITICAL ?? 0);

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>Security overview</h1>
          <p>
            Real-time alert telemetry from Alert Service.{' '}
            {lastRefreshed ? `Updated at ${lastRefreshed.toLocaleTimeString()}` : 'Awaiting data…'}
          </p>
        </div>
        <button type="button" onClick={() => void refresh()} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {error && (
        <div className="notice error" role="alert">
          <span>{error}</span>
          <button type="button" className="btn-secondary" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      <div className="metrics">
        <Metric label="Total alerts" value={stats?.totalAlerts} />
        <Metric label="Open alerts" value={stats?.openAlerts} variant="open" />
        <Metric label="High + critical" value={highAndCritical} variant="critical" />
        <Metric label="Acknowledged" value={stats?.acknowledgedAlerts} />
        <Metric label="Resolved" value={stats?.resolvedAlerts} variant="resolved" />
      </div>

      <div className="notice info">
        <div>
          <strong>SOC Telemetry Notice:</strong> Real-time snapshot is active. Historical timeseries
          trend aggregation is not currently exposed by Alert Service REST endpoints (
          <code>GET /api/alerts/statistics</code> aggregates current alert states). Timeseries
          charting will activate once time-bucketed telemetry is supported.
        </div>
      </div>

      <div className="charts">
        <article className="panel">
          <h2>Severity breakdown</h2>
          {severityData.length === 0 ? (
            <p>No severity distribution data available.</p>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={severityData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={12} tickLine={false} />
                <YAxis allowDecimals={false} stroke="#94a3b8" fontSize={12} tickLine={false} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0e1626',
                    borderColor: '#1e2d4a',
                    color: '#f1f5f9',
                    borderRadius: '0.375rem',
                  }}
                />
                <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                  {severityData.map((entry) => (
                    <Cell key={entry.name} fill={severityColors[entry.name] ?? '#38bdf8'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </article>

        <article className="panel">
          <h2>Threat types</h2>
          {threatData.length === 0 ? (
            <p>No threat distribution data available.</p>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie
                  data={threatData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={80}
                  innerRadius={45}
                  paddingAngle={4}
                >
                  {threatData.map((entry, i) => (
                    <Cell key={entry.name} fill={threatColors[i % threatColors.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0e1626',
                    borderColor: '#1e2d4a',
                    color: '#f1f5f9',
                    borderRadius: '0.375rem',
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          )}
        </article>
      </div>

      <article className="panel">
        <div className="panel-heading">
          <h2>Recent alerts</h2>
          <Link to="/alerts">View all alerts →</Link>
        </div>

        {loading && !recent ? (
          <p>Loading alerts…</p>
        ) : recent?.items && recent.items.length > 0 ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Severity</th>
                  <th>Threat Type</th>
                  <th>Source IP</th>
                  <th>Detected</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {recent.items.map((alert) => (
                  <tr key={alert.alertId}>
                    <td>
                      <span className={`badge ${alert.severity.toLowerCase()}`}>
                        {alert.severity}
                      </span>
                    </td>
                    <td>
                      <Link to={`/alerts/${alert.alertId}`}>
                        <strong>{alert.threatType}</strong>
                      </Link>
                    </td>
                    <td>
                      <code>{alert.sourceIp || 'N/A'}</code>
                    </td>
                    <td>{new Date(alert.detectedAt).toLocaleString()}</td>
                    <td>
                      <span className={`status-badge status-${alert.status.toLowerCase()}`}>
                        {alert.status}
                      </span>
                    </td>
                    <td>
                      <Link to={`/alerts/${alert.alertId}`}>Investigate →</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p>No alerts have been recorded.</p>
        )}
      </article>
    </section>
  );
}
