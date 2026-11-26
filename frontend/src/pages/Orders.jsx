import { useState } from 'react';
import { ArrowRight, ClipboardList, MapPin, Plus, Search, ShoppingCart, Trash2, UserPlus } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import PaymentDialog, { PaymentTag } from '../components/PaymentDialog';
import OrderProgress from '../components/OrderProgress';
import OrderTable from '../components/OrderTable';
import { Button, DataTable, Dialog, Empty, ErrorNotice, Field, FormSubmit, Loading, PageTitle, Section, Status } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { errorMessage, makeRequestKey, post, put } from '../services/api';
import { dateLabel, fullName, locationLabel, money, normalize, orderCode, statusName, timeLabel } from '../services/format';

export default function Orders() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const orders = useResource('/pedidos');
  const allowed = user.role !== 'ALMACEN';
  const filter = params.get('estado') || '';
  const query = params.get('q') || '';
  const modal = allowed && (params.get('nuevo') ? 'order' : params.get('clientes') ? 'customer' : null);
  const detail = params.get('pedido');
  const assignedOnly = user.role === 'ALMACEN' && params.get('vista') === 'asignados';
  const rows = orders.data?.filter(({ order, customer }) => (!assignedOnly || (user.workerId != null && order.warehouseWorkerId === user.workerId))
    && (!filter || order.status === filter)
    && normalize(customer + ' ' + orderCode(order.id)).includes(normalize(query).trim()));
  function update(key, value) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: key === 'q' });
  }
  const close = () => { const next = new URLSearchParams(params); ['nuevo', 'clientes', 'pedido'].forEach((key) => next.delete(key)); setParams(next); };
  function created(id) {
    orders.reload();
    setParams({ pedido: id });
  }

  return <>
    <PageTitle title="Pedidos" subtitle="Consulta el estado de tus pedidos y coordina su preparación.">
      {allowed && <><Button icon={UserPlus} variant="outline" onClick={() => update('clientes', 1)}>Clientes</Button>
        <Button icon={Plus} onClick={() => update('nuevo', 1)}>Nuevo pedido</Button></>}
    </PageTitle>
    <ErrorNotice message={orders.error} retry={orders.reload} />
    <Section title="Listado de pedidos" className="table-panel" icon={ShoppingCart} action={<span className="result-count">{rows?.length ?? 0} pedidos</span>}>
      <div className="filters">
        <label className="input-icon"><Search size={17} /><input aria-label="Buscar pedidos" placeholder="Buscar por cliente o código" value={query} onChange={(event) => update('q', event.target.value)} /></label>
        {user.role === 'ALMACEN' && <select aria-label="Filtrar por asignación" value={assignedOnly ? 'asignados' : ''} onChange={(event) => update('vista', event.target.value)}>
          <option value="">Pedidos visibles</option><option value="asignados">Mis asignados</option>
        </select>}
        <select aria-label="Filtrar por estado" value={filter} onChange={(event) => update('estado', event.target.value)}><option value="">Todos los estados</option>
          {Object.entries(statusName).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
      </div>
      {orders.loading && !orders.data ? <Loading /> : <OrderTable rows={rows} empty="No hay pedidos que coincidan con la búsqueda." />}
    </Section>
    {modal === 'order' && <NewOrder close={close} saved={created} />}
    {modal === 'customer' && <Customers close={close} />}
    {detail && !modal && <OrderDetail key={detail} id={detail} close={close} reload={orders.reload} user={user} />}
  </>;
}

function NewOrder({ close, saved }) {
  const customers = useResource('/clientes');
  const products = useResource('/productos', { stock: 'activo' });
  const [requestKey] = useState(makeRequestKey);
  const [items, setItems] = useState([]);
  const [query, setQuery] = useState('');
  const [productId, setProductId] = useState('');
  const [customerId, setCustomerId] = useState('');
  const product = products.data?.find((row) => row.id === Number(productId));
  const available = products.data?.filter((row) => row.quantity > 0 && !items.some((item) => item.product.id === row.id)
    && normalize(row.sku + ' ' + row.name).includes(normalize(query).trim()));
  const total = items.reduce((value, item) => value + item.product.price * item.quantity, 0);
  function add() {
    if (!product || items.some((item) => item.product.id === product.id)) return;
    setItems([...items, { product, quantity: 1 }]); setProductId(''); setQuery('');
  }
  function quantity(id, value) {
    setItems(items.map((item) => item.product.id === id ? { ...item, quantity: value === '' ? '' : Number(value) } : item));
  }
  async function submit() {
    const result = await post('/pedidos', {
      customerId: Number(customerId),
      items: items.map(({ product: row, quantity: count }) => ({ productId: row.id, quantity: count })),
    }, { headers: { 'Idempotency-Key': requestKey } });
    saved(result.order.id);
  }
  return <Dialog title="Registrar pedido" onClose={close} wide>
    <ErrorNotice message={customers.error || products.error} />
    <FormSubmit onSubmit={submit} onClose={close} label="Registrar pedido" disabled={!items.length || !customerId}>
      <Field label="Cliente">
        <select required value={customerId} onChange={(event) => setCustomerId(event.target.value)}><option value="">Selecciona un cliente</option>
          {customers.data?.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.document}</option>)}</select>
      </Field>
      {!customers.loading && !customers.data?.length && <p className="muted form-note">Registra un cliente en la sección Clientes.</p>}
      <Field label="Buscar producto"><input value={query} placeholder="Nombre o SKU" onChange={(event) => { setQuery(event.target.value); setProductId(''); }} /></Field>
      <div className="inline-control">
        <select aria-label="Producto para el pedido" value={productId} onChange={(event) => setProductId(event.target.value)}>
          <option value="">Selecciona un producto disponible</option>{available?.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.quantity} u. · {money(item.price)}</option>)}
        </select>
        <Button onClick={add} icon={Plus} disabled={!productId} variant="outline">Agregar</Button>
      </div>
      <div className="order-items">
        {items.map(({ product: row, quantity: count }) => <div className="order-item" key={row.id}>
          <div><strong>{row.name}</strong><small>{row.sku} · {money(row.price)} / u.</small></div>
          <input type="number" aria-label={`Cantidad de ${row.name}`} min="1" max={Math.min(row.quantity, 1000000)} required value={count} onChange={(event) => quantity(row.id, event.target.value)} />
          <strong>{money(row.price * count)}</strong>
          <button type="button" className="icon-button danger" aria-label={`Quitar ${row.name}`} onClick={() => setItems(items.filter((item) => item.product.id !== row.id))}><Trash2 size={17} /></button>
        </div>)}
      </div>
      <div className="order-total"><span>Total del pedido</span><strong>{money(total)}</strong></div>
    </FormSubmit>
  </Dialog>;
}

function Customers({ close }) {
  const customers = useResource('/clientes');
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  async function create(values) {
    await post('/clientes', Object.fromEntries(values));
    customers.reload(); setCreating(false);
  }
  return <Dialog title={creating ? 'Registrar cliente' : 'Clientes'} onClose={close} wide>
    {creating ? <FormSubmit onSubmit={create} onClose={() => setCreating(false)} label="Registrar cliente">
      <Field label="Nombre completo"><input name="name" required maxLength={150} /></Field>
      <Field label="Documento"><input name="document" required maxLength={30} /></Field>
      <Field label="Correo electrónico (opcional)"><input name="email" type="email" maxLength={180} /></Field>
    </FormSubmit> : <>
      <div className="filters dialog-filters"><label className="input-icon"><Search size={17} /><input aria-label="Buscar clientes" placeholder="Nombre o documento" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
        <Button icon={Plus} onClick={() => setCreating(true)}>Nuevo cliente</Button></div>
      <ErrorNotice message={customers.error} retry={customers.reload} />
      {customers.loading && !customers.data ? <Loading /> : <DataTable rows={customers.data?.filter((item) => normalize(item.name + ' ' + item.document).includes(normalize(query)))}
        columns={[{ key: 'name', label: 'Nombre' }, { key: 'document', label: 'Documento' }, { key: 'email', label: 'Correo', render: (item) => item.email || '—' }]} />}
    </>}
  </Dialog>;
}

const nextStates = {
  PENDIENTE: ['ACEPTADO', 'CANCELADO'], ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['LISTO', 'CANCELADO'], LISTO: ['ENTREGADO', 'CANCELADO'], ENTREGADO: [], CANCELADO: [],
};
const actionLabels = { ACEPTADO: 'Aceptar pedido', EN_PREPARACION: 'Iniciar preparación', LISTO: 'Marcar como listo', ENTREGADO: 'Confirmar entrega', CANCELADO: 'Cancelar pedido' };

function OrderDetail({ id, close, reload, user }) {
  const payment = useResource(user.role === 'ALMACEN' ? null : `/pedidos/${id}/pago`);
  const [paying, setPaying] = useState(false);
  const record = useResource(`/pedidos/${id}`);
  const products = useResource('/productos');
  const locations = useResource('/ubicaciones');
  const allowedWorkers = useResource(user.role === 'ADMIN' ? '/trabajadores' : null);
  const [targetWorker, setTargetWorker] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [cancelPrompt, setCancelPrompt] = useState(false);
  const order = record.data?.order;
  const next = order ? nextStates[order.status].filter((state) => user.role === 'ADMIN'
    || (user.role === 'VENDEDOR' ? state === 'ENTREGADO' || (state === 'CANCELADO' && order.status === 'PENDIENTE')
      : ['ACEPTADO', 'EN_PREPARACION', 'LISTO'].includes(state))) : [];
  const assigned = allowedWorkers.data?.find(({ worker }) => worker.id === order?.warehouseWorkerId)?.worker;

  async function transition(status) {
    setBusy(true); setError('');
    try {
      if (status === 'ACEPTADO') await post(`/pedidos/${id}/aceptar`, targetWorker ? { workerId: Number(targetWorker) } : {});
      else await put(`/pedidos/${id}/estado`, { status });
      record.reload(); payment.reload(); reload(); setCancelPrompt(false);
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <>
    <Dialog title={orderCode(id)} onClose={close} wide>
    <ErrorNotice message={error || record.error || products.error || locations.error || allowedWorkers.error} retry={record.error ? record.reload : undefined} />
    {record.loading && !order ? <Loading /> : order && <>
      <div className="detail-summary"><div><strong>{record.data.customer}</strong><small>{dateLabel(order.createdAt)} · {timeLabel(order.createdAt)}{assigned ? ' · ' + fullName(assigned) : ''}</small></div><Status value={order.status} /></div>
      <OrderProgress status={order.status} />
      {user.role !== 'ALMACEN' && <div className="order-payment-status">
        <span>Pago</span>
        {payment.data?.status === 'PENDIENTE' && order.status === 'ENTREGADO'
          ? <span className="muted">Sin cobro registrado</span> : payment.data && <PaymentTag status={payment.data.status} />}
        {payment.data?.payment ? <Button variant="quiet" onClick={() => setPaying(true)}>Ver comprobante</Button>
          : payment.data?.status === 'PENDIENTE' && order.status !== 'ENTREGADO' && <Button variant="outline" onClick={() => setPaying(true)}>Cobrar pedido</Button>}
      </div>}
      <ErrorNotice message={payment.error} retry={payment.reload} />
      <DataTable rows={record.data.items} columns={[
        { key: 'product', label: 'Producto', render: (item) => products.data?.find((p) => p.id === item.productId)?.name || `Producto #${item.productId}` },
        { key: 'location', label: 'Ubicación', render: (item) => {
          const product = products.data?.find((p) => p.id === item.productId);
          const location = locations.data?.find((item) => item.id === product?.locationId);
          return location ? <Link className="location-text table-link" to={`/almacen?ubicacion=${location.id}`}><MapPin size={14} />{locationLabel(location)}</Link> : '—';
        } },
        { key: 'quantity', label: 'Cantidad', align: 'right' },
        { key: 'price', label: 'Importe', align: 'right', render: (item) => money(item.quantity * item.price) },
      ]} />
      <div className="order-total"><span>Total del pedido</span><strong>{money(order.total)}</strong></div>
      <h3 className="form-heading"><ClipboardList size={18} /> Historial de estados</h3>
      {record.data.history.length ? <ol className="timeline">{record.data.history.map((entry) => <li key={entry.id}>
        <Status value={entry.status} /><span>{dateLabel(entry.createdAt)} · {timeLabel(entry.createdAt)} · Usuario #{entry.userId}</span>
      </li>)}</ol> : <Empty>No hay historial.</Empty>}
      {next.includes('ACEPTADO') && user.role === 'ADMIN' && <Field label="Asignar trabajador disponible">
        <select value={targetWorker} onChange={(event) => setTargetWorker(event.target.value)}><option value="">Selecciona un trabajador</option>
          {allowedWorkers.data?.filter((item) => item.available).map(({ worker }) => <option value={worker.id} key={worker.id}>{fullName(worker)}</option>)}
        </select>
      </Field>}
      {cancelPrompt ? <div className="confirm-box"><p>El pedido se cancelará y las unidades reservadas volverán al inventario.</p>
        <div className="dialog-actions"><Button variant="outline" onClick={() => setCancelPrompt(false)}>Volver</Button>
          <Button variant="destructive" disabled={busy} onClick={() => transition('CANCELADO')}>Confirmar cancelación</Button></div></div>
        : next.length > 0 && <div className="dialog-actions">{[...next].reverse().map((state) => <Button key={state} icon={state === 'CANCELADO' ? undefined : ArrowRight}
          variant={state === 'CANCELADO' ? 'outline' : 'primary'} disabled={busy || record.loading || (state === 'ENTREGADO' && payment.data?.status !== 'PAGADO') || (state === 'ACEPTADO' && user.role === 'ADMIN' && !targetWorker)}
          onClick={() => state === 'CANCELADO' ? setCancelPrompt(true) : transition(state)}>{actionLabels[state]}</Button>)}</div>}
    </>}
    </Dialog>
    {paying && <PaymentDialog orderId={id} close={() => setPaying(false)} onPaid={payment.reload} />}
  </>;
}
