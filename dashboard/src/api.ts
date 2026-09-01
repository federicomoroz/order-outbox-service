import { NOTIFICATION_API_URL, ORDER_API_URL } from './config';
import type { CreateOrderRequest, NotificationDto, OrderDto, OutboxEventDto } from './types';

/** Error de API con el status HTTP a la vista, para poder distinguir 4xx de 5xx en la UI. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function getJson<T>(url: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new ApiError(response.status, `GET ${url} respondio ${response.status}`);
  }
  return (await response.json()) as T;
}

export const fetchOrders = (signal: AbortSignal): Promise<OrderDto[]> =>
  getJson<OrderDto[]>(`${ORDER_API_URL}/api/orders`, signal);

export const fetchOutboxEvents = (signal: AbortSignal): Promise<OutboxEventDto[]> =>
  getJson<OutboxEventDto[]>(`${ORDER_API_URL}/api/outbox`, signal);

export const fetchNotifications = (signal: AbortSignal): Promise<NotificationDto[]> =>
  getJson<NotificationDto[]>(`${NOTIFICATION_API_URL}/api/notifications`, signal);

/**
 * `POST /api/orders`. Devuelve 201 sin tocar Kafka: la orden y su fila de outbox se escriben en
 * una sola transaccion de Postgres, y el relay publica despues.
 */
export async function createOrder(request: CreateOrderRequest): Promise<OrderDto> {
  const response = await fetch(`${ORDER_API_URL}/api/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let detail = `${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body?.message) {
        detail = `${response.status} — ${body.message}`;
      }
    } catch {
      // El cuerpo del error no siempre es JSON; el status ya alcanza para mostrar algo util.
    }
    throw new ApiError(response.status, detail);
  }

  return (await response.json()) as OrderDto;
}
