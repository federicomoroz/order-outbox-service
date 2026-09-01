import type { OutboxStatus } from '../types';

/**
 * El color aca es semantico, no decorativo: ambar = en el outbox esperando relay,
 * verde = confirmado por Kafka, rojo = agoto los reintentos. Es el unico lugar del panel donde
 * se usan los tres acentos juntos.
 */
export function StatusPill({ status }: { status: OutboxStatus }): React.JSX.Element {
  return (
    <span className={`pill pill--${status.toLowerCase()}`}>
      <span className="pill__dot" aria-hidden="true" />
      {status}
    </span>
  );
}
