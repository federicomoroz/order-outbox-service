import { NOTIFICATION_API_URL, ORDER_API_URL, POLL_INTERVAL_MS } from '../config';
import type { ServiceHealth } from '../types';

interface AppHeaderProps {
  orderServiceHealth: ServiceHealth;
  notificationServiceHealth: ServiceHealth;
  lastUpdatedAt: number | null;
  now: number;
}

const HEALTH_LABEL: Record<ServiceHealth, string> = {
  ok: 'en linea',
  down: 'sin respuesta',
  unknown: 'conectando…',
};

function ServiceChip({ name, url, health }: { name: string; url: string; health: ServiceHealth }) {
  return (
    <span className={`chip chip--${health}`} title={`${url} — ${HEALTH_LABEL[health]}`}>
      <span className="chip__dot" aria-hidden="true" />
      <span className="chip__name">{name}</span>
      <span className="chip__state">{HEALTH_LABEL[health]}</span>
    </span>
  );
}

export function AppHeader({
  orderServiceHealth,
  notificationServiceHealth,
  lastUpdatedAt,
  now,
}: AppHeaderProps): React.JSX.Element {
  const staleSeconds = lastUpdatedAt === null ? null : Math.round((now - lastUpdatedAt) / 1000);

  return (
    <header className="app-header">
      <div className="app-header__brand">
        <svg className="app-header__mark" viewBox="0 0 32 32" aria-hidden="true">
          <rect width="32" height="32" rx="8" fill="#0D1F3C" stroke="#1E3A5F" />
          <circle cx="8" cy="16" r="2.6" fill="#3B82F6" />
          <circle cx="16" cy="16" r="2.6" fill="#FBBF24" />
          <circle cx="24" cy="16" r="2.6" fill="#22C55E" />
          <path d="M11 16h2.2M18.8 16H21" stroke="#1E3A5F" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
        <div>
          <h1 className="app-header__title">order-outbox-service</h1>
          <p className="app-header__subtitle">
            Transactional Outbox · Idempotent Consumer — el circuito, en vivo
          </p>
        </div>
      </div>

      <div className="app-header__status">
        <ServiceChip name="order-service" url={ORDER_API_URL} health={orderServiceHealth} />
        <ServiceChip
          name="notification-service"
          url={NOTIFICATION_API_URL}
          health={notificationServiceHealth}
        />
        <span className="app-header__poll num" title={`Polling cada ${POLL_INTERVAL_MS} ms`}>
          {staleSeconds === null
            ? 'sin datos aún'
            : staleSeconds <= 2
              ? `actualizado · poll ${POLL_INTERVAL_MS} ms`
              : `último dato hace ${staleSeconds}s`}
        </span>
      </div>
    </header>
  );
}
