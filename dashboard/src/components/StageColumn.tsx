import type { ReactNode } from 'react';

interface StageColumnProps {
  step: number;
  title: string;
  /** De donde salen fisicamente estos datos: servicio + tabla. */
  source: string;
  count: number;
  accent: 'blue' | 'amber' | 'green';
  emptyMessage: string;
  children: ReactNode;
  /** Cuando el servicio que sirve esta columna no responde. */
  offline?: boolean;
}

/** Contenedor de una de las tres etapas del circuito. Solo layout: no sabe que muestra. */
export function StageColumn({
  step,
  title,
  source,
  count,
  accent,
  emptyMessage,
  children,
  offline = false,
}: StageColumnProps): React.JSX.Element {
  return (
    <section className={`stage stage--${accent}`} aria-label={title}>
      <header className="stage__header">
        <span className="stage__step" aria-hidden="true">
          {step}
        </span>
        <div className="stage__titles">
          <h2 className="stage__title">{title}</h2>
          <p className="stage__source">{source}</p>
        </div>
        <span className="stage__count num" title={`${count} registros`}>
          {count}
        </span>
      </header>

      <div className="stage__body">
        {offline ? (
          <p className="stage__empty stage__empty--error">Servicio sin respuesta.</p>
        ) : count === 0 ? (
          <p className="stage__empty">{emptyMessage}</p>
        ) : (
          <ul className="stage__list">{children}</ul>
        )}
      </div>
    </section>
  );
}
