import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DemoLoginPage } from '../src/features/DemoLoginPage';

const { submitDemoLogin } = vi.hoisted(() => ({ submitDemoLogin: vi.fn() }));
vi.mock('../src/services/demoAuth', () => ({ submitDemoLogin }));

describe('demo login page', () => {
  it('validates required fields', () => {
    render(<DemoLoginPage />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByRole('status')).toHaveTextContent('required');
  });
  it('shows loading and successful submission', async () => {
    submitDemoLogin.mockResolvedValueOnce('Demo login successful.');
    render(<DemoLoginPage />);
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByRole('button')).toHaveTextContent('Submitting');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('successful'));
  });
  it('shows a safe failure message', async () => {
    submitDemoLogin.mockRejectedValueOnce(new Error('offline'));
    render(<DemoLoginPage />);
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button'));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Login failed.'));
  });
});
