/** Helpers de presentacion. Nada de logica de negocio aca: solo como se ve un dato. */

const TIME_FORMATTER = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

/** `14:03:27` en la zona horaria del navegador, a partir del ISO-8601 UTC que manda la API. */
export function formatClock(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '—' : TIME_FORMATTER.format(date);
}

/** `hace 3s` / `hace 2m` — resolucion suficiente para un panel que refresca cada segundo. */
export function formatRelative(iso: string | null | undefined, now: number): string {
  if (!iso) return '—';
  const timestamp = new Date(iso).getTime();
  if (Number.isNaN(timestamp)) return '—';

  const seconds = Math.max(0, Math.round((now - timestamp) / 1000));
  if (seconds < 1) return 'recien';
  if (seconds < 60) return `hace ${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `hace ${minutes}m`;
  return `hace ${Math.floor(minutes / 60)}h`;
}

/** Diferencia en milisegundos entre dos instantes ISO, o `null` si falta alguno. */
export function elapsedMs(fromIso: string | null | undefined, toIso: string | null | undefined): number | null {
  if (!fromIso || !toIso) return null;
  const from = new Date(fromIso).getTime();
  const to = new Date(toIso).getTime();
  if (Number.isNaN(from) || Number.isNaN(to)) return null;
  return Math.max(0, to - from);
}

/** `1,84 s` / `320 ms` — latencias cortas se leen mejor en ms. */
export function formatDuration(ms: number | null): string {
  if (ms === null) return '—';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2).replace('.', ',')} s`;
}

/** Prefijo de un UUID, lo suficiente para reconocerlo de un vistazo sin ocupar toda la fila. */
export function shortId(id: string): string {
  return id.length <= 8 ? id : id.slice(0, 8);
}

/** `19,99 USD` con separador decimal local. */
export function formatMoney(amount: number, currency: string): string {
  return `${amount.toFixed(2).replace('.', ',')} ${currency}`;
}

/** Mediana, no promedio: una sola publicacion lenta no deberia mover la metrica. */
export function median(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? Math.round((sorted[middle - 1] + sorted[middle]) / 2) : sorted[middle];
}
