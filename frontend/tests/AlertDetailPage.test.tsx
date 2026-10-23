import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertDetailPage } from '../src/features/AlertDetailPage';

const { getAlert, changeStatus } = vi.hoisted(() => ({
  getAlert: vi.fn(),
  changeStatus: vi.fn(),
}));

vi.mock('../src/services/alertApi', () => ({
  getAlert,
  changeStatus,
  getApiErrorMessage: (_err: unknown, fallback: string) => fallback,
}));

const mockAlert = {
  alertId: 'a1',
  ruleId: 'RULE-101',
  threatType: 'PORT_SCAN',
  severity: 'HIGH',
  sourceIp: '10.0.0.1',
  description: 'Possible port scan detected',
  evidence: { distinctDestinationPorts: 20 },
  firstSeen: '2026-01-01T00:00:00Z',
  lastSeen: '2026-01-01T00:00:00Z',
  detectedAt: '2026-01-01T00:00:00Z',
  status: 'OPEN',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('alert detail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders and resolves an alert', async () => {
    getAlert.mockResolvedValue(mockAlert);
    changeStatus.mockResolvedValue({ ...mockAlert, status: 'RESOLVED' });

    render(
      <MemoryRouter initialEntries={['/alerts/a1']}>
        <Routes>
          <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('PORT_SCAN')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Resolve' }));

    await waitFor(() => expect(changeStatus).toHaveBeenCalledWith('a1', 'resolve'));
    expect(await screen.findByText('RESOLVED')).toBeInTheDocument();
  });

  it('renders and acknowledges an OPEN alert', async () => {
    getAlert.mockResolvedValue(mockAlert);
    changeStatus.mockResolvedValue({ ...mockAlert, status: 'ACKNOWLEDGED' });

    render(
      <MemoryRouter initialEntries={['/alerts/a1']}>
        <Routes>
          <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('PORT_SCAN')).toBeInTheDocument();
    const ackButton = screen.getByRole('button', { name: 'Acknowledge' });
    expect(ackButton).toBeInTheDocument();

    fireEvent.click(ackButton);
    await waitFor(() => expect(changeStatus).toHaveBeenCalledWith('a1', 'acknowledge'));
    expect(await screen.findByText('ACKNOWLEDGED')).toBeInTheDocument();
  });

  it('locks resolution and hides action buttons when status is RESOLVED', async () => {
    getAlert.mockResolvedValue({ ...mockAlert, status: 'RESOLVED' });

    render(
      <MemoryRouter initialEntries={['/alerts/a1']}>
        <Routes>
          <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('PORT_SCAN')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument();
    expect(screen.getByText(/Resolution locked/)).toBeInTheDocument();
  });

  it('displays error notice when alert load fails', async () => {
    getAlert.mockRejectedValue(new Error('Alert not found'));

    render(
      <MemoryRouter initialEntries={['/alerts/missing']}>
        <Routes>
          <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Unable to load alert details.')).toBeInTheDocument();
  });

  it('safely handles null or empty evidence', async () => {
    getAlert.mockResolvedValue({ ...mockAlert, evidence: null });

    render(
      <MemoryRouter initialEntries={['/alerts/a1']}>
        <Routes>
          <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('PORT_SCAN')).toBeInTheDocument();
    expect(
      screen.getByText('// No additional evidence payload attached to this alert'),
    ).toBeInTheDocument();
  });
});
