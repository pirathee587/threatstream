import type { ReactNode } from 'react';
import { Activity, Bell, Shield, LogIn, Network, Settings, Radio } from 'lucide-react';
import { NavLink } from 'react-router-dom';

const navigation = [
  { label: 'Overview', to: '/', icon: Activity },
  { label: 'Alerts', to: '/alerts', icon: Bell },
  { label: 'Security Events', to: '/security-events', icon: Radio },
  { label: 'Network Analysis', to: '/network-analysis', icon: Network },
  { label: 'Settings', to: '/settings', icon: Settings },
  { label: 'Demo Login', to: '/demo-login', icon: LogIn },
];

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <aside>
        <div className="brand">
          <Shield size={24} />
          <span>ThreatStream</span>
        </div>
        <nav>
          {navigation.map(({ label, to, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'}>
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div
          style={{
            marginTop: 'auto',
            padding: '1rem 0.5rem',
            borderTop: '1px solid var(--border-subtle)',
            fontSize: '0.8rem',
            color: 'var(--text-muted)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              color: '#34d399',
              fontWeight: 600,
            }}
          >
            <span
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: '#10b981',
                display: 'inline-block',
                boxShadow: '0 0 6px #10b981',
              }}
            />
            SOC Engine Active
          </div>
          <div style={{ marginTop: '0.35rem' }}>ThreatStream v0.1.0</div>
        </div>
      </aside>
      <main>{children}</main>
    </div>
  );
}
