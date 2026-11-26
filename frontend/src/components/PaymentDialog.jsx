import { useState } from 'react';
import { Printer } from 'lucide-react';
import { Button, Dialog, ErrorNotice, Field, FormSubmit, Loading, Tag } from './ui';
import { useResource } from '../hooks/useResource';
import { post } from '../services/api';
import { dateLabel, money, orderCode, timeLabel } from '../services/format';

export const paymentMethods = { EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta', YAPE: 'Yape', PLIN: 'Plin' };
const statusLabels = { PENDIENTE: 'Pendiente', PAGADO: 'Pagado', ANULADO: 'Anulado' };
const statusTones = { PENDIENTE: 'amber', PAGADO: 'green', ANULADO: 'red' };

export function PaymentTag({ status }) {
  return <Tag tone={statusTones[status]} dot>{statusLabels[status] || status}</Tag>;
}

export default function PaymentDialog({ orderId, close, onPaid }) {
  const endpoint = `/pedidos/${orderId}/pago`;
  const resource = useResource(endpoint);
  const [stored, setStored] = useState(null);
  const view = stored || resource.data;
  const [method, setMethod] = useState('EFECTIVO');
  const [received, setReceived] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const change = Number(received) - (view?.total ?? 0);
  return <Dialog title={view?.payment ? 'Comprobante interno' : 'Cobrar pedido'} onClose={close}>
    <ErrorNotice message={resource.error} retry={resource.reload} />
    {resource.loading && !view ? <Loading /> : view && (view.payment
      ? <Receipt view={view} />
      : view.status === 'ANULADO' ? <p className="form-note">El pedido está cancelado.</p>
        : <>
          <div className="order-total"><span>{orderCode(orderId)} · {view.customer}</span><strong>{money(view.total)}</strong></div>
          <FormSubmit onClose={close} label="Registrar pago" disabled={method === 'EFECTIVO' ? received === '' || change < 0 : !confirmed}
            onSubmit={async () => {
              const saved = await post(endpoint, { method, received: method === 'EFECTIVO' ? received : null, confirmed });
              setStored(saved);
              onPaid?.(saved);
            }}>
            <Field label="Método de pago"><select value={method} onChange={(event) => { setMethod(event.target.value); setConfirmed(false); }}>
              {Object.entries(paymentMethods).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
            </select></Field>
            {method === 'EFECTIVO' ? <>
              <Field label="Efectivo recibido (S/)"><input type="number" min={view.total} step="0.01" required value={received} onChange={(event) => setReceived(event.target.value)} /></Field>
              <div className="order-total"><span>Cambio</span><strong>{received === '' || change < 0 ? '—' : money(change)}</strong></div>
            </> : <label className="checkbox-row"><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /> Confirmo el registro manual de este pago académico.</label>}
          </FormSubmit>
        </>)}
  </Dialog>;
}

function Receipt({ view }) {
  const record = view.payment;
  return <>
    <article className="receipt-print">
      <h3>Control360 · INT-{String(record.id).padStart(6, '0')}</h3>
      <p>Comprobante interno sin valor fiscal</p>
      <dl>
        <dt>Pedido</dt><dd>{orderCode(view.orderId)}</dd>
        <dt>Cliente</dt><dd>{view.customer}</dd>
        <dt>Fecha</dt><dd>{dateLabel(record.recordedAt)} · {timeLabel(record.recordedAt)}</dd>
        <dt>Método</dt><dd>{paymentMethods[record.method]}</dd>
        <dt>Estado</dt><dd><PaymentTag status={view.status} /></dd>
        <dt>Total</dt><dd><strong>{money(record.amount)}</strong></dd>
        {record.method === 'EFECTIVO' && <>
          <dt>Recibido</dt><dd>{money(record.cashReceived)}</dd>
          <dt>Cambio</dt><dd>{money(record.cashChange)}</dd>
        </>}
        {record.voidedAt && <><dt>Anulado el</dt><dd>{dateLabel(record.voidedAt)} · {timeLabel(record.voidedAt)}</dd></>}
      </dl>
    </article>
    <div className="dialog-actions"><Button icon={Printer} onClick={() => window.print()}>Imprimir comprobante</Button></div>
  </>;
}
