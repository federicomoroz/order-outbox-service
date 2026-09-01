import { formatMoney, formatRelative, shortId } from '../format';
import type { FlashKind } from '../hooks/useRowFlash';
import type { OrderDto } from '../types';

interface OrderRowProps {
  order: OrderDto;
  flash: FlashKind | undefined;
  now: number;
}

export function OrderRow({ order, flash, now }: OrderRowProps): React.JSX.Element {
  return (
    <li className={`row row--blue${flash ? ` row--flash-${flash}` : ''}`}>
      <div className="row__top">
        <span className="row__title">{order.productId}</span>
        <span className="row__value num">
          {formatMoney(order.unitPriceAmount * order.quantity, order.unitPriceCurrency)}
        </span>
      </div>
      <div className="row__meta">
        {/* Mismo prefijo que el aggregateId de la columna del outbox: se puede seguir a ojo. */}
        <code className="mono">{shortId(order.id)}</code>
        <span className="row__dot">·</span>
        <span className="num">
          {order.quantity} × {formatMoney(order.unitPriceAmount, order.unitPriceCurrency)}
        </span>
        <time className="row__time num" dateTime={order.createdAt} title={order.createdAt}>
          {formatRelative(order.createdAt, now)}
        </time>
      </div>
    </li>
  );
}
