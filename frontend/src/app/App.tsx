import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/layouts/AppLayout';
import { DemoLoginPage } from '@/features/DemoLoginPage';
import { AlertDashboard } from '@/features/AlertDashboard';
import { AlertDetailPage } from '@/features/AlertDetailPage';
import { AlertListPage } from '@/features/AlertListPage';

const pages = ['Overview', 'Security Events', 'Alerts', 'Network Analysis', 'Settings'];
function Placeholder({ title }: { title: string }) {
  return (
    <section>
      <h1>{title}</h1>
      <p>This section is being prepared for Phase 2.</p>
    </section>
  );
}
export function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <Routes>
          <Route path="/" element={<AlertDashboard />} />
          <Route path="/demo-login" element={<DemoLoginPage />} />
          <Route path="/alerts" element={<AlertListPage />} />
          <Route path="/alerts/:alertId" element={<AlertDetailPage />} />
          {pages
            .slice(1)
            .filter((page) => page !== 'Alerts')
            .map((page) => (
              <Route
                key={page}
                path={`/${page.toLowerCase().replaceAll(' ', '-')}`}
                element={<Placeholder title={page} />}
              />
            ))}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppLayout>
    </BrowserRouter>
  );
}
