import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertListPage } from '../src/features/AlertListPage';

const { getAlerts } = vi.hoisted(() => ({ getAlerts: vi.fn() }));
vi.mock('../src/services/alertApi', () => ({
  getAlerts,
  getApiErrorMessage: (_err: unknown, fallback: string) => fallback,
}));

const page = {
  items: [
    {
      alertId: 'a1',
      ruleId: 'RULE',
      threatType: 'BRUTE_FORCE',
      severity: 'HIGH',
      sourceIp: '10.0.0.1',
      description: 'test',
      evidence: {},
      firstSeen: '2026-01-01T00:00:00Z',
      lastSeen: '2026-01-01T00:00:00Z',
      detectedAt: '2026-01-01T00:00:00Z',
      status: 'OPEN',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 45,
  totalPages: 3,
};

describe('alert list', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders results and sends severity filters', async () => {
    getAlerts.mockResolvedValue(page);
    render(
      <BrowserRouter>
        <AlertListPage />
      </BrowserRouter>,
    );

    await screen.findByText('BRUTE_FORCE');
    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'HIGH' } });

    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(expect.objectContaining({ severity: 'HIGH' })),
    );
  });

  it('shows empty results', async () => {
    getAlerts.mockResolvedValue({ ...page, items: [] });
    render(
      <BrowserRouter>
        <AlertListPage />
      </BrowserRouter>,
    );

    expect(await screen.findByText(/No alerts match/)).toBeInTheDocument();
  });

  it('navigates pagination when Next is clicked', async () => {
    const page2 = { ...page, page: 1 };
    getAlerts.mockResolvedValueOnce(page).mockResolvedValueOnce(page2);
    render(
      <BrowserRouter>
        <AlertListPage />
      </BrowserRouter>,
    );

    await screen.findByText('BRUTE_FORCE');
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    const nextBtn = screen.getByRole('button', { name: 'Next' });
    expect(nextBtn).toBeEnabled();

    fireEvent.click(nextBtn);
    await waitFor(
      () => expect(getAlerts).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })),
      { timeout: 10000 },
    );
  });

  it('handles sort field and direction changes', async () => {
    getAlerts.mockResolvedValue(page);
    render(
      <BrowserRouter>
        <AlertListPage />
      </BrowserRouter>,
    );

    await screen.findByText('BRUTE_FORCE');
    fireEvent.change(screen.getByLabelText('Sort by'), { target: { value: 'severity' } });

    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(expect.objectContaining({ sortBy: 'severity' })),
    );

    fireEvent.change(screen.getByLabelText('Order'), { target: { value: 'ASC' } });
    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(expect.objectContaining({ direction: 'ASC' })),
    );
  });

  it('handles status and threat type filter updates', async () => {
    getAlerts.mockResolvedValue(page);
    render(
      <BrowserRouter>
        <AlertListPage />
      </BrowserRouter>,
    );

    await screen.findByText('BRUTE_FORCE');
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'OPEN' } });
    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'OPEN' })),
    );

    fireEvent.change(screen.getByLabelText('Threat type'), { target: { value: 'PORT_SCAN' } });
    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(
        expect.objectContaining({ threatType: 'PORT_SCAN' }),
      ),
    );
  });

  it('resets filters when Reset Filters button is clicked', async () => {
    getAlerts.mockResolvedValue(page);
    render(
      <BrowserRouter>
        <AlertListPage />
      </BrowserRouter>,
    );

    await screen.findByText('BRUTE_FORCE');
    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'CRITICAL' } });
    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(expect.objectContaining({ severity: 'CRITICAL' })),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Reset Filters' }));
    await waitFor(() =>
      expect(getAlerts).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          size: 20,
          sortBy: 'detectedAt',
          direction: 'DESC',
        }),
      ),
    );
  });
});
