import { AlertTriangle, BellRing, Boxes, ClipboardList, CreditCard, PackageCheck, ShoppingCart, Warehouse } from 'lucide-react';
import { Link } from 'react-router-dom';
import DashboardMetric from '../components/DashboardMetric';
import OrderTable from '../components/OrderTable';
import PersonalAttendance from '../components/PersonalAttendance';
import ProductPhoto from '../components/ProductPhoto';
import { Empty, ErrorNotice, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { money } from '../services/format';

export function SellerDashboard() {
  const orders = useResource('/pedidos', {}, 15000);
  const inventory = useResource('/productos', { stock: 'activo' });
  const payments = useResource('/pagos', {}, 15000);
  const paidTotal = (payments.data || []).filter((item) => item.status === 'PAGADO').reduce((total, item) => total + item.payment.amount, 0);
  const attendance = useResource('/asistencias/estado', {}, 15000);
  const rows = orders.data || [];
  const pending = rows.filter(({ order }) => order.status === 'PENDIENTE');
  const ready = rows.filter(({ order }) => order.status === 'LISTO');
  const delivered = rows.filter(({ order }) => order.status === 'ENTREGADO');
  const available = (inventory.data || []).filter((item) => item.quantity > 0);
  return <>
    <PageTitle title="Panel de ventas" subtitle="Registra pedidos, consulta su preparación y confirma las entregas.">
      <Link className="button button--primary" to="/punto-de-venta"><ShoppingCart size={17} /> Punto de venta</Link>
    </PageTitle>
    <ErrorNotice message={orders.error || payments.error} retry={() => { orders.reload(); payments.reload(); }} />
    {orders.data && <div className="metrics-grid">
      <DashboardMetric icon={CreditCard} tone="teal" title="Total cobrado" value={payments.data ? money(paidTotal) : '—'} label="Mis pagos vigentes" to="/pagos" />
      <DashboardMetric icon={ClipboardList} tone="amber" title="Pedidos pendientes" value={pending.length} label="Por aceptar en almacén" to="/pedidos?estado=PENDIENTE" />
      <DashboardMetric icon={BellRing} tone="amber" title="Listos para entregar" value={ready.length} label="Pendientes de confirmación" to="/pedidos?estado=LISTO" />
      <DashboardMetric icon={PackageCheck} tone="green" title="Entregados" value={delivered.length} label="Entregas confirmadas" to="/pedidos?estado=ENTREGADO" />
    </div>}
    {ready.length > 0 && <div className="ready-order-notice" role="status">
      <BellRing size={19} />
      <span>{ready.length === 1 ? 'Tienes un pedido listo para entregar.' : `Tienes ${ready.length} pedidos listos para entregar.`}</span>
      <TextLink to="/pedidos?estado=LISTO">Ver listos</TextLink>
    </div>}
    <div className="dashboard-grid staff-dashboard">
      <Section title="Mis pedidos recientes" icon={ShoppingCart} action={<TextLink to="/pedidos">Ver historial</TextLink>} className="table-panel recent-panel">
        {orders.loading && !orders.data ? <Loading /> : <OrderTable rows={rows.slice(0, 6)} empty="Todavía no tienes pedidos registrados." />}
      </Section>
      <div className="staff-dashboard-side">
        <PersonalAttendance resource={attendance} />
        <Section title="Productos disponibles" icon={Boxes} action={<TextLink to="/inventario?stock=activo">Ver inventario</TextLink>}>
          <ErrorNotice message={inventory.error} retry={inventory.reload} />
          {inventory.loading && !inventory.data ? <Loading /> : available.length ? <div className="map-products">
            {available.slice(0, 3).map((product) => <Link key={product.id} to={`/inventario?q=${encodeURIComponent(product.sku)}`}>
              <ProductPhoto product={product} />
              <div><strong>{product.name}</strong><small>{product.sku} · {money(product.price)}</small></div>
              <Tag tone="green">{product.quantity} u.</Tag>
            </Link>)}
          </div> : <Empty>No hay productos con existencias.</Empty>}
        </Section>
      </div>
    </div>
  </>;
}

export function WarehouseDashboard() {
  const { user } = useAuth();
  const orders = useResource('/pedidos', {}, 15000);
  const attendance = useResource('/asistencias/estado', {}, 15000);
  const stock = useResource('/productos', { stock: 'bajo' }, 15000);
  const incidents = useResource('/incidencias', {}, 20000);
  const openIncidents = incidents.data?.filter((item) => ['PENDIENTE', 'EN_REVISION'].includes(item.status)).length;
  const rows = orders.data || [];
  const pending = rows.filter(({ order }) => order.status === 'PENDIENTE');
  const assigned = rows.filter(({ order }) => order.warehouseWorkerId != null && order.warehouseWorkerId === user.workerId);
  const preparing = assigned.filter(({ order }) => ['ACEPTADO', 'EN_PREPARACION'].includes(order.status));
  const ready = assigned.filter(({ order }) => order.status === 'LISTO');
  return <>
    <PageTitle title="Panel de almacén" subtitle="Acepta pedidos, ubica sus productos y registra el avance de preparación.">
      <Link className="button button--primary" to="/almacen"><Warehouse size={17} /> Mapa del almacén</Link>
    </PageTitle>
    <ErrorNotice message={orders.error} retry={orders.reload} />
    {orders.data && <div className="metrics-grid staff-metrics">
      <DashboardMetric icon={ClipboardList} tone="amber" title="Pendientes de aceptar" value={pending.length} label="Disponibles para almacén" to="/pedidos?estado=PENDIENTE" />
      <DashboardMetric icon={Boxes} tone="teal" title="Mis pedidos en curso" value={preparing.length} label="Aceptados o en preparación" to="/pedidos?vista=asignados" />
      <DashboardMetric icon={PackageCheck} tone="green" title="Mis pedidos listos" value={ready.length} label="Listos para entregar" to="/pedidos?vista=asignados&estado=LISTO" />
    </div>}
    <div className="dashboard-grid staff-dashboard">
      <div className="staff-dashboard-side">
        <Section title="Pedidos pendientes" icon={ClipboardList} action={<TextLink to="/pedidos?estado=PENDIENTE">Ver pendientes</TextLink>} className="table-panel recent-panel">
          {orders.loading && !orders.data ? <Loading /> : <OrderTable rows={pending.slice(0, 5)} empty="No hay pedidos pendientes de aceptar." />}
        </Section>
        <Section title="Mis pedidos asignados" icon={Boxes} action={<TextLink to="/pedidos?vista=asignados">Ver asignados</TextLink>} className="table-panel recent-panel">
          {orders.loading && !orders.data ? <Loading /> : <OrderTable rows={assigned.slice(0, 5)} empty="Todavía no tienes pedidos asignados." />}
        </Section>
      </div>
      <div className="staff-dashboard-side">
        <Section title="Alertas e incidencias" icon={AlertTriangle}>
          <ErrorNotice message={stock.error || incidents.error} retry={() => { stock.reload(); incidents.reload(); }} />
          <div className="operations-links">
            <Link to="/alertas-stock">Productos con alerta de stock <strong>{stock.data?.length ?? '—'}</strong></Link>
            <Link to="/incidencias">Mis incidencias abiertas <strong>{openIncidents ?? '—'}</strong></Link>
          </div>
        </Section>
        <PersonalAttendance resource={attendance} />
      </div>
    </div>
  </>;
}
