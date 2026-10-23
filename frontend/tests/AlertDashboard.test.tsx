import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertDashboard } from '../src/features/AlertDashboard';

const { getStatistics, getAlerts } = vi.hoisted(() => ({
  getStatistics: vi.fn(),
  getAlerts: vi.fn(),
}));

vi.mock('../src/services/alertApi', () => ({
  getStatistics,
  getAlerts,
  getApiErrorMessage: (_err: unknown, fallback: string) => fallback,
}));

const mockStats = {
  totalAlerts: 42,
  openAlerts: 12,
  acknowledgedAlerts: 7,
  resolvedAlerts: 20,
  bySeverity: {
    CRITICAL: 5,
    HIGH: 10,
    MEDIUM: 27,
  },
  byThreatType: {
    PORT_SCAN: 25,
    BRUTE_FORCE: 17,
  },
};

const mockPage = {
  items: [
    {
      alertId: 'alert-1',
      ruleId: 'RULE-01',
      threatType: 'SQL_INJECTION',
      severity: 'CRITICAL',
      sourceIp: '198.51.100.22',
      description: 'SQL syntax detected in query parameter',
      evidence: { param: 'id', payload: "' OR 1=1--" },
      firstSeen: '2026-10-23T10:00:00Z',
      lastSeen: '2026-10-23T10:05:00Z',
      detectedAt: '2026-10-23T10:05:00Z',
      status: 'OPEN',
      createdAt: '2026-10-23T10:05:00Z',
      updatedAt: '2026-10-23T10:05:00Z',
    },
  ],
  page: 0,
  size: 6,
  totalElements: 1,
  totalPages: 1,
};

describe('AlertDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders security overview metrics and recent alerts', async () => {
    getStatistics.mockResolvedValue(mockStats);
    getAlerts.mockResolvedValue(mockPage);

    render(
      <BrowserRouter>
        <AlertDashboard />
      </BrowserRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Security overview' })).toBeInTheDocument();

    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('15')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('20')).toBeInTheDocument();

    expect(await screen.findByText('SQL_INJECTION')).toBeInTheDocument();
    expect(screen.getByText('198.51.100.22')).toBeInTheDocument();
    expect(screen.getByText(/SOC Telemetry Notice/)).toBeInTheDocument();
  });

  it('handles error state and allows retry', async () => {
    getStatistics.mockRejectedValueOnce(new Error('Backend offline'));
    getAlerts.mockResolvedValue(mockPage);

    render(
      <BrowserRouter>
        <AlertDashboard />
      </BrowserRouter>,
    );

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Unable to load alert telemetry.')).toBeInTheDocument();

    // Now make it succeed on retry
    getStatistics.mockResolvedValueOnce(mockStats);
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => {
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
    expect(screen.getByText('42')).toBeInTheDocument();
  });

  it('cleans up polling timer on unmount', () => {
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    getStatistics.mockResolvedValue(mockStats);
    getAlerts.mockResolvedValue(mockPage);

    const { unmount } = render(
      <BrowserRouter>
        <AlertDashboard />
      </BrowserRouter>,
    );

    unmount();
    expect(clearIntervalSpy).toHaveBeenCalled();
  });
});
