import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getAlerts, getApiErrorMessage } from '@/services/alertApi';
import type {
  AlertFilters,
  AlertPage,
  AlertSortField,
  AlertStatus,
  Severity,
  SortDirection,
} from '@/types/alerts';

const defaultInterval = Number(import.meta.env.VITE_ALERT_POLL_INTERVAL_MS ?? 30000);

const sortOptions: { label: string; value: AlertSortField }[] = [
  { label: 'Detected At', value: 'detectedAt' },
  { label: 'Severity', value: 'severity' },
  { label: 'Status', value: 'status' },
  { label: 'Threat Type', value: 'threatType' },
  { label: 'Created At', value: 'createdAt' },
];

export function AlertListPage() {
  const [filters, setFilters] = useState<AlertFilters>({
    page: 0,
    size: 20,
    sortBy: 'detectedAt',
    direction: 'DESC',
  });
  const [data, setData] = useState<AlertPage>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date>();
  const request = useRef(0);

  const load = useCallback(async () => {
    const id = ++request.current;
    setLoading(true);
    try {
      const result = await getAlerts(filters);
      if (id === request.current) {
        setData(result);
        setLastUpdated(new Date());
        setError(undefined);
      }
    } catch (cause) {
      if (id === request.current) {
        setError(getApiErrorMessage(cause, 'Unable to load alerts.'));
      }
    } finally {
      if (id === request.current) {
        setLoading(false);
      }
    }
  }, [filters]);

  useEffect(() => {
    void load();
    const intervalMs = Math.max(5000, defaultInterval);
    const timer = window.setInterval(() => {
      void load();
    }, intervalMs);

    return () => {
      window.clearInterval(timer);
      request.current += 1;
    };
  }, [load]);

  function updateFilter<K extends keyof AlertFilters>(key: K, value: AlertFilters[K] | string) {
    setFilters((current) => ({
      ...current,
      [key]: value === '' ? undefined : value,
      page: 0,
    }));
  }

  function resetFilters() {
    setFilters({
      page: 0,
      size: 20,
      sortBy: 'detectedAt',
      direction: 'DESC',
    });
  }

  return (
    <section>
      <div className="page-heading">
        <div>
          <h1>Alerts</h1>
          <p>
            {lastUpdated
              ? `Last refreshed at ${lastUpdated.toLocaleTimeString()} · ${data?.totalElements ?? 0} total alerts`
              : 'Waiting for Alert Service…'}
          </p>
        </div>
        <button type="button" onClick={() => void load()} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      <div className="filters">
        <label htmlFor="severity-filter">
          Severity
          <select
            id="severity-filter"
            aria-label="Severity"
            value={filters.severity ?? ''}
            onChange={(e) => updateFilter('severity', e.target.value as Severity)}
          >
            <option value="">All Severities</option>
            <option value="CRITICAL">CRITICAL</option>
            <option value="HIGH">HIGH</option>
            <option value="MEDIUM">MEDIUM</option>
          </select>
        </label>

        <label htmlFor="status-filter">
          Status
          <select
            id="status-filter"
            aria-label="Status"
            value={filters.status ?? ''}
            onChange={(e) => updateFilter('status', e.target.value as AlertStatus)}
          >
            <option value="">All Statuses</option>
            <option value="OPEN">OPEN</option>
            <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
            <option value="RESOLVED">RESOLVED</option>
          </select>
        </label>

        <label htmlFor="threat-type-filter">
          Threat type
          <input
            id="threat-type-filter"
            aria-label="Threat type"
            placeholder="e.g. PORT_SCAN"
            value={filters.threatType ?? ''}
            onChange={(e) => updateFilter('threatType', e.target.value)}
          />
        </label>

        <label htmlFor="source-ip-filter">
          Source IP
          <input
            id="source-ip-filter"
            aria-label="Source IP"
            placeholder="e.g. 192.168.1.1"
            value={filters.sourceIp ?? ''}
            onChange={(e) => updateFilter('sourceIp', e.target.value)}
          />
        </label>

        <div className="filters-extended">
          <label htmlFor="date-from-filter">
            From (Date)
            <input
              id="date-from-filter"
              aria-label="From"
              type="datetime-local"
              value={filters.from ? filters.from.slice(0, 16) : ''}
              onChange={(e) => {
                const val = e.target.value ? new Date(e.target.value).toISOString() : '';
                updateFilter('from', val);
              }}
            />
          </label>

          <label htmlFor="date-to-filter">
            To (Date)
            <input
              id="date-to-filter"
              aria-label="To"
              type="datetime-local"
              value={filters.to ? filters.to.slice(0, 16) : ''}
              onChange={(e) => {
                const val = e.target.value ? new Date(e.target.value).toISOString() : '';
                updateFilter('to', val);
              }}
            />
          </label>

          <label htmlFor="sort-by-filter">
            Sort by
            <select
              id="sort-by-filter"
              aria-label="Sort by"
              value={filters.sortBy ?? 'detectedAt'}
              onChange={(e) => updateFilter('sortBy', e.target.value as AlertSortField)}
            >
              {sortOptions.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>

          <label htmlFor="direction-filter">
            Order
            <select
              id="direction-filter"
              aria-label="Order"
              value={filters.direction ?? 'DESC'}
              onChange={(e) => updateFilter('direction', e.target.value as SortDirection)}
            >
              <option value="DESC">Descending (Newest / Highest)</option>
              <option value="ASC">Ascending (Oldest / Lowest)</option>
            </select>
          </label>

          <button
            type="button"
            className="btn-secondary"
            onClick={resetFilters}
            title="Reset filters to default"
          >
            Reset Filters
          </button>
        </div>
      </div>

      {error && (
        <div className="notice error" role="alert">
          <span>{error}</span>
          <button type="button" className="btn-secondary" onClick={() => void load()}>
            Retry
          </button>
        </div>
      )}

      {loading && !data ? (
        <p>Loading alerts…</p>
      ) : data?.items && data.items.length > 0 ? (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Severity</th>
                  <th>Risk</th>
                  <th>Threat Type</th>
                  <th>Description</th>
                  <th>Source IP</th>
                  <th>Detected</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((alert) => (
                  <tr key={alert.alertId}>
                    <td>
                      <span className={`badge ${alert.severity.toLowerCase()}`}>
                        {alert.severity}
                      </span>
                    </td>
                    <td>{typeof alert.riskScore === 'number' ? `${alert.riskScore}/100` : '—'}</td>
                    <td>
                      <Link to={`/alerts/${alert.alertId}`}>
                        <strong>{alert.threatType}</strong>
                      </Link>
                    </td>
                    <td style={{ maxWidth: '280px', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {alert.description}
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

          <div className="pagination">
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} alerts
            </span>

            <div className="pagination-controls">
              <label
                htmlFor="page-size-select"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.4rem',
                  fontSize: '0.85rem',
                }}
              >
                Rows:
                <select
                  id="page-size-select"
                  value={filters.size ?? 20}
                  onChange={(e) =>
                    setFilters((prev) => ({ ...prev, size: Number(e.target.value), page: 0 }))
                  }
                  style={{ padding: '0.3rem 0.5rem' }}
                >
                  <option value={10}>10</option>
                  <option value={20}>20</option>
                  <option value={50}>50</option>
                </select>
              </label>

              <button
                type="button"
                className="btn-secondary"
                disabled={loading || data.page === 0}
                onClick={() => setFilters((prev) => ({ ...prev, page: (prev.page ?? 0) - 1 }))}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn-secondary"
                disabled={loading || data.page + 1 >= data.totalPages}
                onClick={() => setFilters((prev) => ({ ...prev, page: (prev.page ?? 0) + 1 }))}
              >
                Next
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="notice">No alerts match the selected filters.</div>
      )}
    </section>
  );
}
