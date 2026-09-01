import { KAFKA_TOPIC, POLL_INTERVAL_MS, RELAY_POLL_INTERVAL_MS } from '../config';

/** Cierre del panel: que significa cada color y de donde sale cada numero. */
export function Legend(): React.JSX.Element {
  return (
    <footer className="legend">
      <div className="legend__states">
        <span className="legend__item">
          <span className="legend__swatch legend__swatch--amber" aria-hidden="true" />
          <strong>PENDING</strong> — commiteado en Postgres, todavía no publicado
        </span>
        <span className="legend__item">
          <span className="legend__swatch legend__swatch--green" aria-hidden="true" />
          <strong>PUBLISHED</strong> — Kafka confirmó el <code>send</code>
        </span>
        <span className="legend__item">
          <span className="legend__swatch legend__swatch--red" aria-hidden="true" />
          <strong>FAILED</strong> — agotó los reintentos rápidos; el relay lo sigue reintentando
          con backoff, no está abandonado
        </span>
      </div>

      <p className="legend__note">
        Relay cada {RELAY_POLL_INTERVAL_MS / 1000}s · topic <code>{KAFKA_TOPIC}</code> · panel por
        polling cada {POLL_INTERVAL_MS} ms sobre <code>GET /api/orders</code>,{' '}
        <code>GET /api/outbox</code> y <code>GET /api/notifications</code>.
      </p>
    </footer>
  );
}
