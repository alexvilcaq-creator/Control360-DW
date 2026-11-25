import { BellRing, Boxes, ClipboardList, PackageCheck, ShoppingCart, Warehouse } from 'lucide-react';
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
  const attendance = useResource('/asistencias/estado', {}, 15000);
  const rows = orders.data || [];
  const active = rows.filter(({ order }) => !['ENTREGADO', 'CANCELADO'].includes(order.status));
  const ready = rows.filter(({ order }) => order.status === 'LISTO');
  const delivered = rows.filter(({ order }) => order.status === 'ENTREGADO');
  const available = (inventory.data || []).filter((item) => item.quantity > 0);
  return <>
    <PageTitle title="Panel de ventas" subtitle="Registra pedidos, consulta su preparación y confirma las entregas.">
      <Link className="button button--primary" to="/pedidos?nuevo=1"><ShoppingCart size={17} /> Nuevo pedido</Link>
    </PageTitle>
    <ErrorNotice message={orders.error} retry={orders.reload} />
    {orders.data && <div className="metrics-grid staff-metrics">
      <DashboardMetric icon={ClipboardList} tone="teal" title="Mis pedidos activos" value={active.length} label="Pendientes, en preparación o listos" to="/pedidos" />
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
      <PersonalAttendance resource={attendance} />
    </div>
  </>;
}
