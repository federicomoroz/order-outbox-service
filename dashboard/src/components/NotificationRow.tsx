import { formatRelative, shortId } from '../format';
import type { FlashKind } from '../hooks/useRowFlash';
import type { NotificationDto } from '../types';

interface NotificationRowProps {
  notification: NotificationDto;
  flash: FlashKind | undefined;
  now: number;
}

export function NotificationRow({ notification, flash, now }: NotificationRowProps): React.JSX.Element {
  return (
    <li className={`row row--green${flash ? ` row--flash-${flash}` : ''}`}>
      <div className="row__top">
        <span className="row__title row__title--message">{notification.message}</span>
      </div>
      <div className="row__meta">
        {/* Es el id de la orden, no el de la notificacion: cierra el circuito con la columna 1. */}
        <code className="mono">{shortId(notification.orderId)}</code>
        <span className="row__dot">·</span>
        <span>exactamente una vez</span>
        <time className="row__time num" dateTime={notification.createdAt} title={notification.createdAt}>
          {formatRelative(notification.createdAt, now)}
        </time>
      </div>
    </li>
  );
}
