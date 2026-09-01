export type MetricTone = 'neutral' | 'blue' | 'amber' | 'green' | 'red';

export interface Metric {
  label: string;
  value: string;
  hint: string;
  tone: MetricTone;
}

/** Fila de contadores derivados del ultimo poll. Ninguno se guarda: todos son estado actual. */
export function MetricsBar({ metrics }: { metrics: readonly Metric[] }): React.JSX.Element {
  return (
    <section className="metrics" aria-label="Métricas del circuito">
      {metrics.map((metric) => (
        <article key={metric.label} className={`metric metric--${metric.tone}`}>
          <p className="metric__label">{metric.label}</p>
          <p className="metric__value num">{metric.value}</p>
          <p className="metric__hint">{metric.hint}</p>
        </article>
      ))}
    </section>
  );
}
