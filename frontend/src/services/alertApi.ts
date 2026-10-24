import axios, { type AxiosRequestConfig } from 'axios';
import type { Alert, AlertFilters, AlertPage, AlertStatistics } from '@/types/alerts';

const baseURL =
  import.meta.env.VITE_ALERT_API_BASE_URL ??
  import.meta.env.VITE_API_BASE_URL ??
  'http://localhost:8083';

const apiKey = import.meta.env.VITE_ALERT_API_KEY ?? import.meta.env.VITE_API_KEY;

export const alertClient = axios.create({
  baseURL,
  headers: apiKey ? { 'X-API-Key': apiKey } : undefined,
  timeout: 10000,
});

// Attach correlation ID to every outgoing request for distributed tracing
alertClient.interceptors.request.use((config) => {
  if (!config.headers.get('X-Correlation-ID')) {
    const generatedId =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `fe-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
    config.headers.set('X-Correlation-ID', generatedId);
  }
  return config;
});

function parameters(filters: AlertFilters): URLSearchParams {
  const values = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      values.set(key, String(value));
    }
  });
  return values;
}

export async function getAlerts(
  filters: AlertFilters = {},
  config?: AxiosRequestConfig,
): Promise<AlertPage> {
  const response = await alertClient.get<AlertPage>('/api/alerts', {
    params: parameters(filters),
    ...config,
  });
  return response.data;
}

export async function getAlert(alertId: string, config?: AxiosRequestConfig): Promise<Alert> {
  const response = await alertClient.get<Alert>(
    `/api/alerts/${encodeURIComponent(alertId)}`,
    config,
  );
  return response.data;
}

export async function getStatistics(config?: AxiosRequestConfig): Promise<AlertStatistics> {
  const response = await alertClient.get<AlertStatistics>('/api/alerts/statistics', config);
  return response.data;
}

export async function changeStatus(
  alertId: string,
  action: 'acknowledge' | 'resolve',
  config?: AxiosRequestConfig,
): Promise<Alert> {
  const response = await alertClient.patch<Alert>(
    `/api/alerts/${encodeURIComponent(alertId)}/${action}`,
    undefined,
    config,
  );
  return response.data;
}

export function getApiErrorMessage(cause: unknown, fallback: string): string {
  if (axios.isAxiosError(cause)) {
    if (cause.response?.status === 401) {
      return 'Authentication failed. Please verify your API key.';
    }
    if (cause.response?.status === 403) {
      return 'Access denied. Analyst permissions required.';
    }
    if (cause.response?.status === 404) {
      return 'Alert not found.';
    }
    if (
      cause.response?.status === 502 ||
      cause.response?.status === 503 ||
      cause.response?.status === 504 ||
      cause.code === 'ECONNREFUSED' ||
      cause.code === 'ERR_NETWORK' ||
      cause.code === 'ECONNABORTED'
    ) {
      return 'Alert Service is temporarily unavailable or recovering. Please retry shortly.';
    }
    if (cause.response?.data && typeof cause.response.data === 'object') {
      const data = cause.response.data as { message?: string; error?: string };
      if (data.message && typeof data.message === 'string') return data.message;
      if (data.error && typeof data.error === 'string') return data.error;
    }
    return cause.message || fallback;
  }
  if (cause instanceof Error) {
    return cause.message;
  }
  return fallback;
}
