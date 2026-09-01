/** Formas de wire de las 3 APIs de lectura. Espejan los records Java de cada servicio. */

export type OutboxStatus = 'PENDING' | 'PUBLISHED' | 'FAILED';

/** `GET /api/orders` — order-service (:8006). */
export interface OrderDto {
  id: string;
  customerId: string;
  productId: string;
  quantity: number;
  unitPriceAmount: number;
  unitPriceCurrency: string;
  status: string;
  createdAt: string;
}

/** `GET /api/outbox` — order-service (:8006). Sin `payload` a proposito. */
export interface OutboxEventDto {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  status: OutboxStatus;
  publishAttempts: number;
  occurredAt: string;
  publishedAt: string | null;
  /** Cuando el relay vuelve a intentar. `null` = ya publicado, o le toca en el proximo poll. */
  nextAttemptAt: string | null;
}

/** `GET /api/notifications` — notification-service (:8007), otra base de datos. */
export interface NotificationDto {
  id: string;
  orderId: string;
  customerId: string;
  message: string;
  createdAt: string;
}

/** Cuerpo de `POST /api/orders`. */
export interface CreateOrderRequest {
  customerId: string;
  productId: string;
  quantity: number;
  unitPriceAmount: number;
  unitPriceCurrency: string;
}

/** Estado de conectividad de cada servicio, derivado del ultimo poll. */
export type ServiceHealth = 'ok' | 'down' | 'unknown';
