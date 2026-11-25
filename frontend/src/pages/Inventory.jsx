import { useState } from 'react';
import { Boxes, History, Package, Plus, Search, SlidersHorizontal } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button, DataTable, Dialog, ErrorNotice, Field, FormSubmit, Loading, PageTitle, Section, Tag } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { post, put } from '../services/api';
import { dateLabel, locationLabel, money, orderCode, timeLabel } from '../services/format';

export default function Inventory() {
  const { user } = useAuth();
  const admin = user.role === 'ADMIN';
  const [params, setParams] = useSearchParams();
  const [modal, setModal] = useState(null);
  const query = params.get('q') || '';
  const category = params.get('category') || '';
  const stock = params.get('stock') || '';
  const products = useResource('/productos', { q: query || undefined, category: category || undefined, stock: stock || undefined });
  const categories = useResource('/categorias');
  const locations = useResource('/ubicaciones');
  const creating = admin && params.get('nuevo');
  function setParam(key, value) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: key === 'q' });
  }
  function close() { setModal(null); if (creating) setParam('nuevo', ''); }
  function saved() { products.reload(); close(); }

  const columns = [
    { key: 'product', label: 'Producto', render: (item) => <div className="entity-cell"><span className="product-tile"><Package size={17} /></span><div><strong>{item.name}</strong><small>{item.sku}</small></div></div> },
    { key: 'category', label: 'Categoría', render: (item) => categories.data?.find((c) => c.id === item.categoryId)?.name || '—' },
    { key: 'location', label: 'Ubicación', render: (item) => <Link className="table-link muted-link" to={`/almacen?ubicacion=${item.locationId}`}>{locationLabel(locations.data?.find((l) => l.id === item.locationId))}</Link> },
    { key: 'price', label: 'Precio', align: 'right', render: (item) => money(item.price) },
    { key: 'quantity', label: 'Existencia', align: 'right', render: (item) => <Tag tone={!item.active ? 'muted' : item.quantity <= item.minStock ? 'red' : 'green'}>{item.quantity} u.</Tag> },
    { key: 'status', label: 'Estado', render: (item) => <span className="muted">{item.active ? 'Activo' : 'Inactivo'}</span> },
  ];
  if (admin) columns.push({ key: 'actions', label: '', align: 'right', render: (item) => <div className="row-actions">
    <Button variant="quiet" onClick={() => setModal({ type: 'edit', item })}>Editar</Button>
    <button className="icon-button" type="button" aria-label={`Ajustar stock de ${item.name}`} onClick={() => setModal({ type: 'adjust', item })}><SlidersHorizontal size={16} /></button>
  </div> });

  return <>
    <PageTitle title="Inventario" subtitle="Consulta productos, ubicaciones y existencias disponibles.">
      {admin && <><Button variant="outline" icon={History} onClick={() => setModal({ type: 'history' })}>Movimientos</Button><Button icon={Plus} onClick={() => setParam('nuevo', '1')}>Nuevo producto</Button></>}
    </PageTitle>
    <ErrorNotice message={products.error || categories.error || locations.error} retry={products.reload} />
    <Section title="Catálogo de productos" icon={Boxes} action={<span className="result-count">{products.data?.length ?? 0} productos</span>}>
      <div className="filters">
        <label className="input-icon"><Search size={17} /><input aria-label="Buscar productos" placeholder="Buscar por nombre o SKU" value={query} onChange={(event) => setParam('q', event.target.value)} /></label>
        <select aria-label="Filtrar por categoría" value={category} onChange={(event) => setParam('category', event.target.value)}><option value="">Todas las categorías</option>
          {categories.data?.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
        <select aria-label="Filtrar por existencias" value={stock} onChange={(event) => setParam('stock', event.target.value)}>
          <option value="">Todas las existencias</option><option value="bajo">Stock bajo</option><option value="agotado">Agotados</option><option value="activo">Productos activos</option>
        </select>
      </div>
      {products.loading && !products.data ? <Loading /> : <DataTable rows={products.data} columns={columns} empty="No hay productos que coincidan con los filtros." />}
    </Section>
    {(creating || modal?.type === 'edit') && <ProductForm close={close} saved={saved} item={modal?.item} categories={categories.data || []} locations={locations.data || []} />}
    {modal?.type === 'adjust' && <Dialog title="Ajustar existencias" onClose={close}>
      <p className="form-note">{modal.item.name} · Existencia actual: <strong>{modal.item.quantity} u.</strong></p>
      <FormSubmit onClose={close} label="Registrar ajuste" onSubmit={async (values) => { await post(`/inventario/${modal.item.id}/ajustes`, { delta: Number(values.get('delta')), note: values.get('note') }); saved(); }}>
        <Field label="Unidades a ajustar" hint="Usa un número positivo para entradas o negativo para salidas."><input name="delta" type="number" min={-modal.item.quantity} max={2147483647 - modal.item.quantity} required /></Field>
        <Field label="Motivo"><textarea name="note" required maxLength={250} rows={3} /></Field>
      </FormSubmit>
    </Dialog>}
    {modal?.type === 'history' && <MovementHistory close={close} />}
  </>;
}

function ProductForm({ item, close, saved, categories, locations }) {
  const ready = categories.length > 0 && locations.length > 0;
  async function submit(values) {
    const body = Object.fromEntries(values);
    for (const name of ['price', 'minStock', 'categoryId', 'locationId']) body[name] = Number(body[name]);
    body.active = values.has('active');
    if (values.has('initialStock')) body.initialStock = Number(values.get('initialStock'));
    if (item) await put(`/productos/${item.id}`, body); else await post('/productos', body);
    saved();
  }
  return <Dialog title={item ? 'Editar producto' : 'Nuevo producto'} onClose={close} wide>
    {!ready && <p className="form-note">Para registrar un producto, crea al menos una <Link to="/categorias" className="table-link">categoría</Link> y una <Link to="/ubicaciones" className="table-link">ubicación</Link>.</p>}
    <FormSubmit onSubmit={submit} onClose={close} disabled={!ready}>
      <div className="form-grid">
        <Field label="Nombre"><input name="name" defaultValue={item?.name} required maxLength={150} /></Field>
        <Field label="SKU"><input name="sku" defaultValue={item?.sku} required maxLength={60} /></Field>
        <Field label="Categoría"><select name="categoryId" required defaultValue={item?.categoryId || ''}><option value="">Seleccionar</option>
          {categories.map((row) => <option key={row.id} value={row.id}>{row.name}</option>)}</select></Field>
        <Field label="Ubicación"><select name="locationId" required defaultValue={item?.locationId || ''}><option value="">Seleccionar</option>
          {locations.map((row) => <option key={row.id} value={row.id}>{locationLabel(row)}</option>)}</select></Field>
        <Field label="Precio (S/)"><input name="price" type="number" min="0" max="9999999999.99" step="0.01" defaultValue={item?.price} required /></Field>
        <Field label="Stock mínimo"><input name="minStock" type="number" min="0" max="2147483647" defaultValue={item?.minStock ?? 0} required /></Field>
        {!item && <Field label="Existencia inicial"><input name="initialStock" type="number" min="0" max="2147483647" defaultValue="0" required /></Field>}
      </div>
      <label className="checkbox-row"><input name="active" type="checkbox" defaultChecked={item?.active ?? true} /> Producto activo</label>
    </FormSubmit>
  </Dialog>;
}

function MovementHistory({ close }) {
  const products = useResource('/productos');
  const [product, setProduct] = useState('');
  const history = useResource('/inventario/movimientos', { productId: product || undefined });
  const byId = Object.fromEntries((products.data || []).map((item) => [item.id, item.name]));
  return <Dialog title="Movimientos de inventario" onClose={close} wide>
    <Field label="Filtrar por producto"><select value={product} onChange={(event) => setProduct(event.target.value)}><option value="">Todos los productos</option>
      {products.data?.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
    <ErrorNotice message={products.error || history.error} retry={history.reload} />
    {history.loading && !history.data ? <Loading /> : <DataTable rows={history.data} columns={[
      { key: 'product', label: 'Producto', render: (item) => byId[item.productId] || `#${item.productId}` },
      { key: 'type', label: 'Movimiento', render: (item) => <div className="cell-stack"><strong>{({ RESERVA: 'Reserva', DEVOLUCION: 'Devolución', AJUSTE: 'Ajuste' })[item.type]}</strong>
        <small>{item.orderId ? orderCode(item.orderId) : item.note}</small></div> },
      { key: 'delta', label: 'Unidades', align: 'right', render: (item) => <Tag tone={item.delta > 0 ? 'green' : 'red'}>{item.delta > 0 ? '+' : ''}{item.delta}</Tag> },
      { key: 'balance', label: 'Saldo', align: 'right' },
      { key: 'createdAt', label: 'Fecha', render: (item) => dateLabel(item.createdAt) + ' · ' + timeLabel(item.createdAt) },
    ]} />}
  </Dialog>;
}
