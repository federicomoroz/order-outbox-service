import { useEffect, useRef, useState } from 'react';
import { ROW_FLASH_MS } from '../config';

export type FlashKind = 'new' | 'changed';

/**
 * Detecta que filas aparecieron y cuales cambiaron de estado entre dos polls, para poder
 * resaltarlas un instante. Es lo que hace visible la transicion PENDING -> PUBLISHED: sin esto,
 * el cambio ocurre entre dos renders y nadie lo ve.
 *
 * La primera carga no dispara nada — si no, entrar al panel encenderia las 50 filas de golpe.
 *
 * @param items      coleccion del ultimo poll
 * @param keyOf      identidad estable de una fila (su id)
 * @param signatureOf lo que, al cambiar, cuenta como "esta fila se movio" (status, intentos...)
 */
export function useRowFlash<T>(
  items: readonly T[],
  keyOf: (item: T) => string,
  signatureOf: (item: T) => string,
): Readonly<Record<string, FlashKind>> {
  const [flashes, setFlashes] = useState<Record<string, FlashKind>>({});
  const previousRef = useRef<Map<string, string> | null>(null);
  const timersRef = useRef<Map<string, number>>(new Map());

  useEffect(() => {
    const current = new Map(items.map((item) => [keyOf(item), signatureOf(item)] as const));

    // Primer poll: solo sembramos la referencia, sin encender nada.
    if (previousRef.current === null) {
      previousRef.current = current;
      return;
    }

    const changes: Record<string, FlashKind> = {};
    for (const [key, signature] of current) {
      const before = previousRef.current.get(key);
      if (before === undefined) {
        changes[key] = 'new';
      } else if (before !== signature) {
        changes[key] = 'changed';
      }
    }
    previousRef.current = current;

    const changedKeys = Object.keys(changes);
    if (changedKeys.length === 0) return;

    setFlashes((active) => ({ ...active, ...changes }));

    // Los timers viven en un ref, no en el cleanup del efecto: el efecto se vuelve a correr una
    // vez por segundo y su cleanup cancelaria el apagado antes de que llegue a ocurrir.
    for (const key of changedKeys) {
      const pending = timersRef.current.get(key);
      if (pending !== undefined) window.clearTimeout(pending);
      timersRef.current.set(
        key,
        window.setTimeout(() => {
          timersRef.current.delete(key);
          setFlashes((active) => {
            const { [key]: _removed, ...rest } = active;
            return rest;
          });
        }, ROW_FLASH_MS),
      );
    }
  }, [items, keyOf, signatureOf]);

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      for (const timer of timers.values()) window.clearTimeout(timer);
      timers.clear();
    };
  }, []);

  return flashes;
}
