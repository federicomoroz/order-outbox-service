import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchNotifications, fetchOrders, fetchOutboxEvents } from '../api';
import { POLL_INTERVAL_MS } from '../config';
import type { NotificationDto, OrderDto, OutboxEventDto, ServiceHealth } from '../types';

export interface CircuitState {
  orders: OrderDto[];
  outboxEvents: OutboxEventDto[];
  notifications: NotificationDto[];
  orderServiceHealth: ServiceHealth;
  notificationServiceHealth: ServiceHealth;
  /** `Date.now()` del ultimo poll que trajo datos; `null` mientras no hubo ninguno. */
  lastUpdatedAt: number | null;
}

const INITIAL_STATE: CircuitState = {
  orders: [],
  outboxEvents: [],
  notifications: [],
  orderServiceHealth: 'unknown',
  notificationServiceHealth: 'unknown',
  lastUpdatedAt: null,
};

/**
 * Un unico `setInterval` que consulta los 3 endpoints de lectura en paralelo.
 *
 * Tres decisiones que valen la pena:
 * - `Promise.allSettled`, no `Promise.all`: si `notification-service` se cae, las columnas de
 *   `order-service` tienen que seguir vivas. Cada servicio tiene su propio estado de salud.
 * - Guarda `inFlight`: si un ciclo tarda mas que el intervalo, no se encolan requests encima.
 * - `AbortController` compartido: al desmontar se cancelan los fetch en vuelo en vez de dejarlos
 *   resolver contra un componente que ya no existe.
 */
export function useCircuit(): CircuitState & { refreshNow: () => void } {
  const [state, setState] = useState<CircuitState>(INITIAL_STATE);
  const tickRef = useRef<() => void>(() => {});

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;
    let inFlight = false;

    const tick = async (): Promise<void> => {
      if (inFlight || cancelled) return;
      inFlight = true;
      try {
        const [orders, outbox, notifications] = await Promise.allSettled([
          fetchOrders(controller.signal),
          fetchOutboxEvents(controller.signal),
          fetchNotifications(controller.signal),
        ]);

        if (cancelled) return;

        setState((previous) => {
          const orderServiceUp = orders.status === 'fulfilled' && outbox.status === 'fulfilled';
          return {
            orders: orders.status === 'fulfilled' ? orders.value : previous.orders,
            outboxEvents: outbox.status === 'fulfilled' ? outbox.value : previous.outboxEvents,
            notifications:
              notifications.status === 'fulfilled' ? notifications.value : previous.notifications,
            orderServiceHealth: orderServiceUp ? 'ok' : 'down',
            notificationServiceHealth: notifications.status === 'fulfilled' ? 'ok' : 'down',
            lastUpdatedAt: orderServiceUp ? Date.now() : previous.lastUpdatedAt,
          };
        });
      } finally {
        inFlight = false;
      }
    };

    tickRef.current = () => {
      void tick();
    };
    void tick();
    const intervalId = window.setInterval(() => void tick(), POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
      controller.abort();
    };
  }, []);

  // Para no esperar hasta un segundo despues de crear una orden a que aparezca en el panel.
  const refreshNow = useCallback(() => tickRef.current(), []);

  return { ...state, refreshNow };
}
