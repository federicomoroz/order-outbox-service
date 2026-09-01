interface FlowConnectorProps {
  /** Que hace que el dato pase de la etapa anterior a la siguiente. */
  label: string;
  /** Detalle tecnico (intervalo del relay, topic de Kafka...). */
  detail: string;
  /** `true` mientras haya algo efectivamente viajando por este tramo. */
  active: boolean;
}

/**
 * El tramo entre dos etapas. El punto que lo recorre solo se mueve cuando `active` es cierto:
 * un panel que anima siempre no informa nada, uno que anima cuando algo pasa, si.
 */
export function FlowConnector({ label, detail, active }: FlowConnectorProps): React.JSX.Element {
  return (
    <div className={`connector${active ? ' connector--active' : ''}`} aria-hidden="true">
      <div className="connector__track">
        <span className="connector__dot" />
        <span className="connector__head" />
      </div>
      <p className="connector__label">{label}</p>
      <p className="connector__detail">{detail}</p>
    </div>
  );
}
