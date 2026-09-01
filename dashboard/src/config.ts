/**
 * Configuracion del panel.
 *
 * Las URLs de las APIs son variables de build de Vite (`VITE_*`), nunca literales `localhost`
 * escritos en el codigo: el bundle se compila una vez dentro del Dockerfile y ahi se decide a
 * que host apunta. Los defaults sirven para `npm run dev` contra el `docker compose` local.
 */

const trimSlash = (url: string): string => url.replace(/\/+$/, '');

export const ORDER_API_URL = trimSlash(
  (import.meta.env.VITE_ORDER_API_URL as string | undefined) ?? 'http://localhost:8006',
);

export const NOTIFICATION_API_URL = trimSlash(
  (import.meta.env.VITE_NOTIFICATION_API_URL as string | undefined) ?? 'http://localhost:8007',
);

/**
 * Polling en vez de SSE/WebSocket: decision deliberada de simplicidad. Con 3 endpoints de
 * lectura acotados y un relay que ya funciona por poll cada 2s, un `setInterval` de 1s muestra
 * la transicion PENDING -> PUBLISHED con resolucion de sobra y sin agregar una capa de
 * streaming (ni su reconexion, ni su backpressure) a un demo de dos servicios.
 */
export const POLL_INTERVAL_MS = 1000;

/** Cada cuanto pollea el relay del outbox en order-service (`outbox.relay.poll-interval-ms`). */
export const RELAY_POLL_INTERVAL_MS = 2000;

/** Topic de Kafka por el que viaja el evento entre los dos servicios. */
export const KAFKA_TOPIC = 'order.created.v1';

/** Cuanto dura el resaltado de una fila que acaba de aparecer o cambiar de estado. */
export const ROW_FLASH_MS = 1600;
