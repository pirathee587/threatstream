import axios, { type AxiosRequestConfig } from 'axios';
import type { Alert, AlertFilters, AlertPage, AlertStatistics } from '@/types/alerts';

const baseURL =
  import.meta.env.VITE_ALERT_API_BASE_URL ??
  import.meta.env.VITE_API_BASE_URL ??
  'http://localhost:8083';

export const alertClient = axios.create({ baseURL });

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
    if (cause.response?.data && typeof cause.response.data === 'object') {
      const data = cause.response.data as { message?: string; error?: string };
      if (data.message) return data.message;
      if (data.error) return data.error;
    }
    if (cause.response?.status === 404) {
      return 'Alert not found.';
    }
    if (
      cause.response?.status === 503 ||
      cause.code === 'ECONNREFUSED' ||
      cause.code === 'ERR_NETWORK'
    ) {
      return 'Alert Service is unavailable.';
    }
    return cause.message || fallback;
  }
  if (cause instanceof Error) {
    return cause.message;
  }
  return fallback;
}
