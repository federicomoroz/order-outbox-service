import { useMemo } from 'react';
import { AppHeader } from './components/AppHeader';
import { CreateOrderPanel } from './components/CreateOrderPanel';
import { FlowConnector } from './components/FlowConnector';
import { Legend } from './components/Legend';
import { MetricsBar, type Metric } from './components/MetricsBar';
import { NotificationRow } from './components/NotificationRow';
import { OrderRow } from './components/OrderRow';
import { OutboxRow } from './components/OutboxRow';
import { StageColumn } from './components/StageColumn';
import { KAFKA_TOPIC, RELAY_POLL_INTERVAL_MS } from './config';
import { elapsedMs, formatDuration, median } from './format';
import { useCircuit } from './hooks/useCircuit';
import { useRowFlash } from './hooks/useRowFlash';
import type { NotificationDto, OrderDto, OutboxEventDto } from './types';

// Identidades y "firmas" de cambio, a nivel de modulo para que sean estables entre renders.
const orderKey = (order: OrderDto): string => order.id;
const orderSignature = (order: OrderDto): string => order.status;

const outboxKey = (event: OutboxEventDto): string => event.id;
// Lo que hace que una fila del outbox "se mueva": su estado y sus intentos de publicacion.
const outboxSignature = (event: OutboxEventDto): string => `${event.status}:${event.publishAttempts}`;

const notificationKey = (notification: NotificationDto): string => notification.id;
const notificationSignature = (notification: NotificationDto): string => notification.createdAt;

export default function App(): React.JSX.Element {
  const {
    orders,
    outboxEvents,
    notifications,
    orderServiceHealth,
    notificationServiceHealth,
    lastUpdatedAt,
    refreshNow,
  } = useCircuit();

  const now = Date.now();

  const orderFlashes = useRowFlash(orders, orderKey, orderSignature);
  const outboxFlashes = useRowFlash(outboxEvents, outboxKey, outboxSignature);
  const notificationFlashes = useRowFlash(notifications, notificationKey, notificationSignature);

  const pending = outboxEvents.filter((event) => event.status === 'PENDING').length;
  const published = outboxEvents.filter((event) => event.status === 'PUBLISHED').length;
  const failed = outboxEvents.filter((event) => event.status === 'FAILED').length;

  // El tramo 1 se enciende cuando acaba de entrar una orden, no mientras "haya ordenes": un
  // conector que anima siempre deja de significar algo.
  const RECENT_ORDER_WINDOW_MS = 5000;
  const justCreatedAnOrder = orders.some(
    (order) => now - new Date(order.createdAt).getTime() < RECENT_ORDER_WINDOW_MS,
  );

  const relayLatency = useMemo(() => {
    const samples = outboxEvents
      .map((event) => elapsedMs(event.occurredAt, event.publishedAt))
      .filter((value): value is number => value !== null);
    return median(samples);
  }, [outboxEvents]);

  const metrics: Metric[] = [
    {
      label: 'Órdenes',
      value: String(orders.length),
      hint: 'últimas 50 · postgres-orders',
      tone: 'blue',
    },
    {
      label: 'Outbox PENDING',
      value: String(pending),
      hint: 'commiteado, sin publicar',
      tone: pending > 0 ? 'amber' : 'neutral',
    },
    {
      label: 'Outbox PUBLISHED',
      value: String(published),
      hint: 'confirmado por Kafka',
      tone: 'green',
    },
    {
      label: 'Outbox FAILED',
      value: String(failed),
      // FAILED dejo de significar "abandonado": la fila se sigue reintentando sola con backoff.
      hint: failed > 0 ? 'degradado, sigue reintentando' : 'sin eventos degradados',
      tone: failed > 0 ? 'red' : 'neutral',
    },
    {
      label: 'Latencia relay',
      value: formatDuration(relayLatency),
      hint: 'mediana commit → ack',
      tone: 'neutral',
    },
    {
      label: 'Notificaciones',
      value: String(notifications.length),
      hint: 'postgres-notifications',
      tone: 'green',
    },
  ];

  return (
    <div className="app">
      <AppHeader
        orderServiceHealth={orderServiceHealth}
        notificationServiceHealth={notificationServiceHealth}
        lastUpdatedAt={lastUpdatedAt}
        now={now}
      />

      <main className="app__main">
        <MetricsBar metrics={metrics} />

        <CreateOrderPanel onCreated={refreshNow} />

        <div className="board">
          <StageColumn
            step={1}
            title="Órdenes"
            source="order-service · tabla orders"
            count={orders.length}
            accent="blue"
            offline={orderServiceHealth === 'down'}
            emptyMessage="Todavía no hay órdenes. Creá una arriba."
          >
            {orders.map((order) => (
              <OrderRow key={order.id} order={order} flash={orderFlashes[order.id]} now={now} />
            ))}
          </StageColumn>

          <FlowConnector
            label="misma transacción"
            detail="1 commit de Postgres"
            active={justCreatedAnOrder}
          />

          <StageColumn
            step={2}
            title="Outbox"
            source="order-service · tabla outbox_events"
            count={outboxEvents.length}
            accent="amber"
            offline={orderServiceHealth === 'down'}
            emptyMessage="El outbox está vacío."
          >
            {outboxEvents.map((event) => (
              <OutboxRow key={event.id} event={event} flash={outboxFlashes[event.id]} now={now} />
            ))}
          </StageColumn>

          <FlowConnector
            label={`relay ${RELAY_POLL_INTERVAL_MS / 1000}s → Kafka`}
            detail={KAFKA_TOPIC}
            active={pending > 0}
          />

          <StageColumn
            step={3}
            title="Notificaciones"
            source="notification-service · tabla notifications"
            count={notifications.length}
            accent="green"
            offline={notificationServiceHealth === 'down'}
            emptyMessage="Ninguna notificación consumida todavía."
          >
            {notifications.map((notification) => (
              <NotificationRow
                key={notification.id}
                notification={notification}
                flash={notificationFlashes[notification.id]}
                now={now}
              />
            ))}
          </StageColumn>
        </div>

        <Legend />
      </main>
    </div>
  );
}
