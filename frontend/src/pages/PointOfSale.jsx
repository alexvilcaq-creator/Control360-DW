import { useState } from 'react';
import { Barcode, CheckCircle2, CreditCard, Plus, Search, ShoppingCart, Trash2 } from 'lucide-react';
import PaymentDialog, { PaymentTag, paymentMethods } from '../components/PaymentDialog';
import ProductPhoto from '../components/ProductPhoto';
import { Button, DataTable, Empty, ErrorNotice, Field, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useResource } from '../hooks/useResource';
import { errorMessage, makeRequestKey, post } from '../services/api';
import { dateLabel, money, normalize, orderCode, timeLabel } from '../services/format';

export default function PointOfSale() {
  const products = useResource('/productos', { stock: 'activo' });
  const customers = useResource('/clientes');
  const [query, setQuery] = useState('');
  const [code, setCode] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [cart, setCart] = useState([]);
  const [requestKey, setRequestKey] = useState(makeRequestKey);
  const [saved, setSaved] = useState(null);
  const [paid, setPaid] = useState(null);
  const [paying, setPaying] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const term = normalize(query.trim());
  const byId = new Map((products.data || []).map((product) => [product.id, product]));
  const shown = (products.data || []).filter((item) => normalize(`${item.name} ${item.sku} ${item.barcode || ''}`).includes(term));
  const countById = new Map(cart.map((item) => [item.product.id, Number(item.count)]));
  const total = cart.reduce((amount, item) => amount + Math.round(item.product.price * 100) * Number(item.count), 0) / 100;
  const valid = cart.length > 0 && cart.every(({ product, count }) => Number.isInteger(count) && count > 0
    && count <= Math.min(byId.get(product.id)?.quantity || 0, 1000000));
  const readOnly = busy || Boolean(saved);

  function add(product) {
    if (readOnly) return;
    setError('');
    setMessage('');
    if (!product) { setError('No se encontró un producto con ese código.'); return; }
    const count = countById.get(product.id) || 0;
    if (count >= Math.min(product.quantity, 1000000)) { setError(`Existencia insuficiente para ${product.name}.`); return; }
    if (!countById.has(product.id) && cart.length >= 100) { setError('Un pedido admite hasta 100 productos distintos.'); return; }
    setCart(countById.has(product.id) ? cart.map((item) => item.product.id === product.id ? { ...item, count: count + 1 } : item)
      : [...cart, { product, count: 1 }]);
    setMessage(`Agregado: ${product.name}`);
  }
  function scan(event) {
    event.preventDefault();
    const value = normalize(code.trim());
    if (!value) return;
    const product = (products.data || []).find((item) => normalize(item.barcode) === value)
      || (products.data || []).find((item) => normalize(item.sku) === value);
    add(product);
    setCode('');
  }
  async function confirm() {
    if (!valid || !customerId || readOnly) return;
    setBusy(true); setError(''); setMessage('');
    try {
      const result = await post('/pedidos', {
        customerId: Number(customerId),
        items: cart.map(({ product, count }) => ({ productId: product.id, quantity: count })),
      }, { headers: { 'Idempotency-Key': requestKey } });
      setSaved(result);
      products.reload();
    } catch (cause) {
      setError(errorMessage(cause));
      products.reload();
    } finally { setBusy(false); }
  }
  function reset() {
    setCart([]); setSaved(null); setPaid(null); setCustomerId(''); setError(''); setMessage('');
    setRequestKey(makeRequestKey()); products.reload();
  }

  return <>
    <PageTitle title="Punto de venta" subtitle="Agrega productos, confirma el pedido y registra el cobro.">
      <TextLink to="/pagos">Historial de pagos</TextLink>
    </PageTitle>
    <ErrorNotice message={products.error || customers.error} retry={() => { products.reload(); customers.reload(); }} />
    <div className="pos-grid">
      <Section title="Catálogo" icon={Barcode} action={<span className="result-count">{shown.length} productos</span>}>
        <div className="pos-filters">
          <label className="input-icon"><Search size={17} /><input aria-label="Buscar en el catálogo" placeholder="Nombre, SKU o código de barras"
            value={query} onChange={(event) => setQuery(event.target.value)} /></label>
          <form className="pos-scan" onSubmit={scan}>
            <label className="input-icon"><Barcode size={17} /><input aria-label="Código de barras" placeholder="Escanea y presiona Enter"
              value={code} disabled={readOnly || !products.data} onChange={(event) => setCode(event.target.value)} /></label>
            <Button type="submit" variant="outline" disabled={readOnly || !code.trim()}>Agregar</Button>
          </form>
        </div>
        <div className="pos-feedback"><ErrorNotice message={error} /><span role="status">{message}</span></div>
        {products.loading && !products.data ? <Loading /> : shown.length ? <div className="pos-products">{shown.map((product) =>
          <article key={product.id} className="pos-product">
            <ProductPhoto product={product} />
            <div><strong>{product.name}</strong><small>{product.sku}{product.barcode ? ' · ' + product.barcode : ''}</small></div>
            <div className="pos-product-end">
              <strong>{money(product.price)}</strong>
              <Tag tone={product.quantity > 0 ? 'green' : 'red'}>{product.quantity} u.</Tag>
              <button className="icon-button pos-add" type="button" aria-label={`Agregar ${product.name}`} disabled={readOnly || product.quantity <= (countById.get(product.id) || 0)} onClick={() => add(product)}><Plus size={18} /></button>
            </div>
          </article>)}</div> : <Empty>No hay productos que coincidan.</Empty>}
      </Section>
      <Section title={saved ? orderCode(saved.order.id) : 'Resumen del pedido'} icon={ShoppingCart} className="pos-order"
        action={saved && <PaymentTag status={paid?.status || 'PENDIENTE'} />}>
        {saved ? <div className="pos-result">
          <p><CheckCircle2 size={18} /> Pedido enviado a almacén.</p>
          <div className="order-total"><span>{saved.customer}</span><strong>{money(saved.order.total)}</strong></div>
          <div className="row-actions"><TextLink to={`/pedidos?pedido=${saved.order.id}`}>Ver pedido</TextLink>
            <Button variant="outline" onClick={reset}>Otro pedido</Button></div>
          <Button className="pos-submit" icon={CreditCard} onClick={() => setPaying(true)}>{paid ? 'Ver comprobante' : 'Cobrar pedido'}</Button>
        </div> : <>
          <Field label="Cliente"><select value={customerId} onChange={(event) => setCustomerId(event.target.value)} disabled={busy}>
            <option value="">Selecciona un cliente</option>
            {customers.data?.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.document}</option>)}
          </select></Field>
          <TextLink to="/pedidos?clientes=1">Gestionar clientes</TextLink>
          {cart.length ? <div className="pos-cart">{cart.map(({ product, count }) =>
            <div className="pos-cart-item" key={product.id}>
              <div><strong>{product.name}</strong><small>{money(product.price)} / u.</small></div>
              <input type="number" aria-label={`Cantidad de ${product.name}`} min="1" max={Math.min(byId.get(product.id)?.quantity || 0, 1000000)}
                value={count} disabled={busy} onChange={(event) => setCart(cart.map((item) => item.product.id === product.id
                  ? { ...item, count: event.target.value === '' ? '' : Number(event.target.value) } : item))} />
              <strong>{money(product.price * Number(count))}</strong>
              <button type="button" className="icon-button" disabled={busy} aria-label={`Quitar ${product.name}`}
                onClick={() => setCart(cart.filter((item) => item.product.id !== product.id))}><Trash2 size={16} /></button>
            </div>)}</div> : <Empty>Agrega productos para comenzar.</Empty>}
          {cart.length > 0 && !valid && <p className="form-note">Verifica las cantidades y el stock antes de confirmar.</p>}
          <div className="order-total"><span>Total</span><strong>{money(total)}</strong></div>
          <Button className="pos-submit" disabled={!valid || !customerId || busy} onClick={confirm}>{busy ? 'Confirmando…' : 'Confirmar pedido'}</Button>
        </>}
      </Section>
    </div>
    {paying && <PaymentDialog orderId={saved.order.id} close={() => setPaying(false)} onPaid={setPaid} />}
  </>;
}

export function PaymentHistory() {
  const payments = useResource('/pagos');
  const [selected, setSelected] = useState(null);
  const [status, setStatus] = useState('');
  const rows = (payments.data || []).filter((item) => !status || item.status === status);
  return <>
    <PageTitle title="Historial de pagos" subtitle="Consulta los cobros registrados y sus comprobantes internos." />
    <ErrorNotice message={payments.error} retry={payments.reload} />
    <Section title="Pagos registrados" icon={CreditCard} className="table-panel" action={<span className="result-count">{rows.length} pagos</span>}>
      <div className="filters"><select aria-label="Filtrar pagos por estado" value={status} onChange={(event) => setStatus(event.target.value)}>
        <option value="">Todos los estados</option><option value="PAGADO">Pagados</option><option value="ANULADO">Anulados</option>
      </select></div>
      {payments.loading && !payments.data ? <Loading /> : <DataTable rows={rows.map((item) => ({ ...item, id: item.payment.id }))}
        empty="No hay pagos para esta selección." columns={[
          { key: 'order', label: 'Pedido', render: (item) => <TextLink to={`/pedidos?pedido=${item.orderId}`}>{orderCode(item.orderId)}</TextLink> },
          { key: 'customer', label: 'Cliente' },
          { key: 'method', label: 'Método', render: (item) => paymentMethods[item.payment.method] },
          { key: 'date', label: 'Registrado', render: (item) => dateLabel(item.payment.recordedAt) + ' · ' + timeLabel(item.payment.recordedAt) },
          { key: 'status', label: 'Estado', render: (item) => <PaymentTag status={item.status} /> },
          { key: 'amount', label: 'Importe', align: 'right', render: (item) => money(item.payment.amount) },
          { key: 'receipt', label: '', align: 'right', render: (item) => <Button variant="quiet" onClick={() => setSelected(item.orderId)}>Ver comprobante</Button> },
        ]} />}
    </Section>
    {selected && <PaymentDialog orderId={selected} close={() => setSelected(null)} />}
  </>;
}
