import { Check, Circle, CircleX } from 'lucide-react';
import { statusName } from '../services/format';

const stages = ['PENDIENTE', 'ACEPTADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO'];

export default function OrderProgress({ status }) {
  const current = stages.indexOf(status);
  if (status === 'CANCELADO') return <div className="order-progress-cancelled"><CircleX size={17} /> Pedido cancelado</div>;
  return <ol className="order-progress" aria-label="Progreso del pedido">
    {stages.map((stage, index) => {
      const complete = index < current;
      const active = index === current;
      return <li key={stage} className={complete ? 'complete' : active ? 'current' : ''} aria-current={active ? 'step' : undefined}>
        <span className="order-progress-icon">{complete ? <Check size={12} aria-hidden="true" /> : <Circle size={9} aria-hidden="true" />}</span>
        <span>{statusName[stage]}</span>
      </li>;
    })}
  </ol>;
}
