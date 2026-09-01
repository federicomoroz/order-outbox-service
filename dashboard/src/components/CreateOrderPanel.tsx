import { useState } from 'react';
import { ApiError, createOrder } from '../api';
import { shortId } from '../format';
import { newUuid } from '../uuid';

interface CreateOrderPanelProps {
  /** Se llama despues de cada orden creada, para no esperar al siguiente tick del poll. */
  onCreated: () => void;
}

type Feedback =
  | { kind: 'idle' }
  | { kind: 'working'; text: string }
  | { kind: 'ok'; text: string }
  | { kind: 'error'; text: string };

const CURRENCIES = ['USD', 'EUR', 'ARS'] as const;
const BURST_SIZE = 5;

/**
 * Sin esto el panel es una foto. Genera trafico real contra `POST /api/orders` para poder ver
 * el evento recorrer las tres columnas.
 */
export function CreateOrderPanel({ onCreated }: CreateOrderPanelProps): React.JSX.Element {
  const [customerId, setCustomerId] = useState(newUuid);
  const [productId, setProductId] = useState('sku-demo-1');
  const [quantity, setQuantity] = useState('2');
  const [unitPrice, setUnitPrice] = useState('19.99');
  const [currency, setCurrency] = useState<string>(CURRENCIES[0]);
  const [feedback, setFeedback] = useState<Feedback>({ kind: 'idle' });
  const [busy, setBusy] = useState(false);

  const send = async (howMany: number): Promise<void> => {
    setBusy(true);
    setFeedback({
      kind: 'working',
      text: howMany === 1 ? 'POST /api/orders…' : `POST /api/orders ×${howMany}…`,
    });

    try {
      const created: string[] = [];
      for (let i = 0; i < howMany; i++) {
        const order = await createOrder({
          customerId,
          productId,
          quantity: Number(quantity),
          unitPriceAmount: Number(unitPrice),
          unitPriceCurrency: currency,
        });
        created.push(order.id);
        onCreated();
      }

      setFeedback({
        kind: 'ok',
        text:
          howMany === 1
            ? `201 Created · ${shortId(created[0])} → outbox PENDING`
            : `201 Created ×${howMany} · ${howMany} filas nuevas en el outbox`,
      });
    } catch (error) {
      const text =
        error instanceof ApiError
          ? `Error ${error.message}`
          : 'No se pudo contactar a order-service (¿CORS o servicio caído?)';
      setFeedback({ kind: 'error', text });
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="creator" aria-label="Crear orden">
      <div className="creator__intro">
        <h2 className="creator__title">Crear orden</h2>
        <p className="creator__hint">
          El <code>201</code> vuelve sin tocar Kafka: orden y evento se escriben juntos en una
          transacción de Postgres.
        </p>
      </div>

      <form
        className="creator__form"
        onSubmit={(event) => {
          event.preventDefault();
          void send(1);
        }}
      >
        <label className="field field--wide">
          <span className="field__label">Producto</span>
          <input
            className="field__input"
            value={productId}
            onChange={(event) => setProductId(event.target.value)}
            required
            maxLength={64}
          />
        </label>

        <label className="field field--narrow">
          <span className="field__label">Cantidad</span>
          <input
            className="field__input num"
            type="number"
            min={1}
            max={999}
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            required
          />
        </label>

        <label className="field field--narrow">
          <span className="field__label">Precio unit.</span>
          <input
            className="field__input num"
            type="number"
            min={0}
            step="0.01"
            value={unitPrice}
            onChange={(event) => setUnitPrice(event.target.value)}
            required
          />
        </label>

        <label className="field field--narrow">
          <span className="field__label">Moneda</span>
          <select
            className="field__input"
            value={currency}
            onChange={(event) => setCurrency(event.target.value)}
          >
            {CURRENCIES.map((code) => (
              <option key={code} value={code}>
                {code}
              </option>
            ))}
          </select>
        </label>

        <div className="field field--customer">
          <span className="field__label">Cliente</span>
          <div className="field__combo">
            <code className="field__readonly mono">{shortId(customerId)}</code>
            <button
              type="button"
              className="button button--ghost"
              onClick={() => setCustomerId(newUuid())}
              title="Generar otro customerId"
            >
              nuevo
            </button>
          </div>
        </div>

        <div className="creator__actions">
          <button type="submit" className="button button--primary" disabled={busy}>
            Crear orden
          </button>
          <button
            type="button"
            className="button button--secondary"
            disabled={busy}
            onClick={() => void send(BURST_SIZE)}
            title="Ráfaga: útil para ver varias filas transicionar a la vez"
          >
            ráfaga ×{BURST_SIZE}
          </button>
        </div>
      </form>

      <p className={`creator__feedback creator__feedback--${feedback.kind}`} role="status">
        {feedback.kind === 'idle' ? ' ' : feedback.text}
      </p>
    </section>
  );
}
