import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../src/app/App';
vi.mock('../src/services/alertApi', () => ({
  getStatistics: () =>
    Promise.resolve({
      totalAlerts: 0,
      openAlerts: 0,
      acknowledgedAlerts: 0,
      resolvedAlerts: 0,
      bySeverity: {},
      byThreatType: {},
    }),
  getAlerts: () =>
    Promise.resolve({ items: [], page: 0, size: 6, totalElements: 0, totalPages: 0 }),
}));
describe('application shell', () => {
  it('shows the security overview', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Security overview' })).toBeInTheDocument();
  });
});
