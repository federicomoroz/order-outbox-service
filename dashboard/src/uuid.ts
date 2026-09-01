/**
 * `crypto.randomUUID()` solo existe en contextos seguros (https o localhost). Si el panel se
 * abre por IP de LAN sobre http, no esta — de ahi el fallback, que no pretende ser
 * criptografico: es un customerId de demo, no un secreto.
 */
export function newUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  const hex = (length: number): string =>
    Array.from({ length }, () => Math.floor(Math.random() * 16).toString(16)).join('');

  const variant = ((Math.floor(Math.random() * 4) + 8) as number).toString(16);
  return `${hex(8)}-${hex(4)}-4${hex(3)}-${variant}${hex(3)}-${hex(12)}`;
}
