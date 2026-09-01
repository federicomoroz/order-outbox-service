import { elapsedMs, formatClock, formatDuration, shortId } from '../format';
import type { FlashKind } from '../hooks/useRowFlash';
import type { OutboxEventDto } from '../types';
import { StatusPill } from './StatusPill';

interface OutboxRowProps {
  event: OutboxEventDto;
  flash: FlashKind | undefined;
}

const ACCENT_BY_STATUS = {
  PENDING: 'row--amber',
  PUBLISHED: 'row--green',
  FAILED: 'row--red',
} as const;

/**
 * La fila mas importante del panel: es la unica vista donde la garantia del patron se ve en
 * lugar de leerse. Muestra el ciclo completo de una fila del outbox — cuando se escribio,
 * cuantas veces se intento publicarla y cuando Kafka la confirmo.
 */
export function OutboxRow({ event, flash }: OutboxRowProps): React.JSX.Element {
  const latency = elapsedMs(event.occurredAt, event.publishedAt);

  return (
    <li
      className={`row ${ACCENT_BY_STATUS[event.status]}${flash ? ` row--flash-${flash}` : ''}`}
    >
      <div className="row__top">
        <StatusPill status={event.status} />
        <span className="row__title row__title--event">{event.eventType}</span>
        {event.publishAttempts > 0 && (
          <span className="row__attempts num" title="Intentos de publicacion del relay">
            {event.publishAttempts} intento{event.publishAttempts === 1 ? '' : 's'}
          </span>
        )}
      </div>

      <div className="row__meta">
        <code className="mono">{shortId(event.aggregateId)}</code>
        <span className="row__dot">·</span>
        <span className="num">{formatClock(event.occurredAt)}</span>

        {event.status === 'PUBLISHED' && (
          <>
            <span className="row__arrow" aria-hidden="true">
              →
            </span>
            <span className="num">{formatClock(event.publishedAt)}</span>
            <span className="row__latency num" title="Tiempo entre el commit y el ack de Kafka">
              +{formatDuration(latency)}
            </span>
          </>
        )}

        {event.status === 'PENDING' && <span className="row__note">esperando al relay…</span>}

        {event.status === 'FAILED' && (
          <span className="row__note row__note--error">sin publicar — revisar manualmente</span>
        )}
      </div>
    </li>
  );
}
