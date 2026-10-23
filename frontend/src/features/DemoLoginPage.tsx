import { type FormEvent, useState } from 'react';
import axios from 'axios';
import { submitDemoLogin } from '@/services/demoAuth';

export function DemoLoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<string>();
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setMessage(undefined);
    if (!username.trim() || !password) {
      setMessage('Username and password are required.');
      return;
    }
    setBusy(true);
    try {
      setMessage(await submitDemoLogin(username.trim(), password));
    } catch (error) {
      setMessage(
        axios.isAxiosError(error)
          ? (error.response?.data?.message ?? 'Login failed.')
          : 'Login failed.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="login-card">
      <h1>Demo Login</h1>
      <p>Submit an attempt to generate a security authentication event.</p>
      <form onSubmit={submit}>
        <label>
          Username
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            maxLength={64}
            autoComplete="username"
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            maxLength={256}
            autoComplete="current-password"
          />
        </label>
        <button disabled={busy} type="submit">
          {busy ? 'Submitting…' : 'Submit demo login'}
        </button>
      </form>
      {message && <p role="status">{message}</p>}
    </section>
  );
}
