import { useState } from 'react';
import { Activity, AlertTriangle, ArrowRight, Boxes, CalendarDays, ChartNoAxesColumnIncreasing, Clock3, CreditCard, PackagePlus, Plus, ShoppingCart, Users, UserRoundCheck, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import Metric from '../components/DashboardMetric';
import { BarPanel } from '../components/charts';
import ProductPhoto from '../components/ProductPhoto';
import OrderTable from '../components/OrderTable';
import { Empty, ErrorNotice, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useResource } from '../hooks/useResource';
import { dateLabel, hours, money } from '../services/format';

export default function Dashboard() {
  const calendar = useResource('/calendario');
  const [date, setDate] = useState('');
  const selected = date || calendar.data?.today;
  const today = selected === calendar.data?.today;
  const [chartMode, setChartMode] = useState('quantity');
  const byValue = chartMode === 'value';
  const dashboard = useResource('/dashboard', selected ? { from: selected, to: selected } : {}, 25000);
  const categories = useResource('/categorias');
  const inventory = useResource(byValue ? '/productos' : null);
  const team = useResource('/trabajadores', {}, 25000);
  const payments = useResource('/pagos', {}, 25000);
  const incidents = useResource('/incidencias', {}, 25000);
  const paidToday = payments.data?.filter((item) => item.status === 'PAGADO' && item.payment.recordedAt.slice(0, 10) === selected);
  const openIncidents = incidents.data?.filter((item) => ['PENDIENTE', 'EN_REVISION'].includes(item.status)).length;
  const countAvailable = team.data?.filter((item) => item.available).length;
  const data = dashboard.data;
  const attendance = data?.attendance;
  const rate = attendance?.scheduled ? Math.min(100, Math.round(100 * attendance.present / attendance.scheduled)) : 0;
  const categoryNames = Object.fromEntries((categories.data || []).map((item) => [item.id, item.name]));
  const chart = byValue ? (categories.data || []).map((category) => ({
    name: category.name,
    value: Math.round((inventory.data || []).filter((product) => product.active && product.categoryId === category.id)
      .reduce((total, product) => total + product.quantity * product.price, 0) * 100) / 100,
  })) : data?.inventoryByCategory;
  const inventoryTotal = chart?.reduce((total, category) => total + category.value, 0) || 0;

  return <>
    <PageTitle title="Resumen de operaciones" subtitle="Pedidos, existencias y actividad del equipo en un solo lugar.">
      <label className="date-control">
        <CalendarDays size={17} /><span>{today ? 'Hoy, ' : ''}{dateLabel(selected)}</span>
        <input type="date" aria-label="Fecha de resumen" value={selected || ''} max={calendar.data?.today} onChange={(event) => setDate(event.target.value)} />
      </label>
    </PageTitle>
    <ErrorNotice message={dashboard.error || calendar.error} retry={dashboard.reload} />
    {dashboard.loading && !data ? <Loading /> : data && <>
      <div className="metrics-grid">
        <Metric icon={ShoppingCart} tone="teal" title="Pedidos del día" value={data.ordersToday} label="En la fecha seleccionada" to="/pedidos" />
        <Metric icon={Clock3} tone="amber" title="Pedidos pendientes" value={data.pendingOrders} label="Por aceptar en almacén" to="/pedidos?estado=PENDIENTE" />
        <Metric icon={Boxes} tone="red" title="Productos con bajo stock" value={data.lowStockCount} label="En el mínimo o por debajo" to="/inventario?stock=bajo" />
        <Metric icon={Users} tone="green" title={today ? 'Trabajadores presentes' : 'Asistencia del personal'} value={`${today ? attendance.atWork : attendance.present} / ${attendance.scheduled}`} label={`${rate}% de asistencia`} to="/asistencia" />
      </div>
      <div className="dashboard-grid dashboard-main">
        <Section title="Pedidos recientes" caption={dateLabel(selected)} icon={ShoppingCart} action={<TextLink to="/pedidos">Ver todos</TextLink>} className="recent-panel table-panel">
          <OrderTable rows={data.recentOrders} />
        </Section>
        <Section title="Inventario por categoría" icon={ChartNoAxesColumnIncreasing} className="inventory-chart"
          action={<select aria-label="Vista de inventario" className="mini-select" value={chartMode} onChange={(event) => setChartMode(event.target.value)}>
            <option value="quantity">Por cantidad</option><option value="value">Por valor</option>
          </select>}>
          <ErrorNotice message={inventory.error} />
          {byValue && inventory.loading ? <Loading /> : <>
            {chart?.length > 0 && <div className="inventory-total"><span>{byValue ? 'Valor de las existencias' : 'Existencia total'}</span>
              <strong>{byValue ? money(inventoryTotal) : inventoryTotal.toLocaleString('es-PE')} {!byValue && <small>u.</small>}</strong></div>}
            <BarPanel data={chart} height={180} unit={byValue ? 'soles' : 'unidades'} accent />
          </>}
        </Section>
      </div>
      <div className="dashboard-grid dashboard-secondary">
        <Section title="Alertas de bajo stock" icon={AlertTriangle} action={<TextLink to="/inventario?stock=bajo">Ver inventario</TextLink>} className="alerts-panel">
          {data.lowStock.length ? <div className="alert-list">{data.lowStock.slice(0, 3).map((product) =>
            <Link to={`/inventario?q=${encodeURIComponent(product.sku)}`} className="stock-alert" key={product.id}>
              <ProductPhoto product={product} />
              <span className="stock-product"><strong>{product.name}</strong><small>{categoryNames[product.categoryId] || product.sku} · Mín. {product.minStock}</small></span>
              <Tag tone="red">{product.quantity} u.</Tag><ArrowRight size={15} className="stock-arrow" />
            </Link>)}</div> : <Empty>No hay productos en el mínimo o por debajo.</Empty>}
        </Section>
        <Section title="Asistencia del equipo" icon={Activity} className="attendance-panel" action={<TextLink to="/asistencia">Historial</TextLink>}>
          <div className="attendance-widget">
            <div className="donut" style={{ '--progress': rate + '%' }}><div><strong>{rate}%</strong><span>Asistencia</span></div></div>
            <div className="attendance-stats"><span><i /> Presentes <strong>{attendance.present}</strong></span>
              <span><i /> Faltas <strong>{attendance.absent}</strong></span>
              <span><i /> Horas extra <strong>{hours(attendance.approvedMinutes)}</strong></span></div>
          </div>
          {countAvailable != null && <div className="team-availability">
            <span><UserRoundCheck size={14} /> <strong>{countAvailable}</strong> disponibles ahora</span>
            <TextLink to="/trabajadores">Ver equipo</TextLink>
          </div>}
          {team.error && <ErrorNotice message={team.error} retry={team.reload} />}
        </Section>
        <Section title="Acciones rápidas" icon={Zap}>
          <div className="quick-grid">
            <Quick to="/punto-de-venta" icon={Plus} title="Abrir punto de venta" tone="teal" />
            <Quick to="/inventario?nuevo=1" icon={PackagePlus} title="Ingresar producto" tone="green" />
            <Quick to="/trabajadores" icon={Users} title="Gestionar equipo" tone="purple" />
            <Quick to="/asistencia?marcar=1" icon={UserRoundCheck} title="Marcar asistencia" tone="teal" />
          </div>
        </Section>
      </div>
      <Section title="Cobros e incidencias" icon={CreditCard}>
        <ErrorNotice message={payments.error || incidents.error} retry={() => { payments.reload(); incidents.reload(); }} />
        <div className="operations-links dashboard-commercial">
          <Link to="/pagos"><span>Cobros de la fecha <small>{paidToday?.length ?? '—'} pagos vigentes</small></span>
            <strong>{paidToday ? money(paidToday.reduce((sum, item) => sum + item.payment.amount, 0)) : '—'}</strong></Link>
          <Link to="/incidencias">Incidencias abiertas <strong>{openIncidents ?? '—'}</strong></Link>
          <Link to="/alertas-stock">Alertas de stock <strong>{data.lowStockCount}</strong></Link>
        </div>
      </Section>
    </>}
  </>;
}

function Quick({ to, icon: Icon, title, tone }) {
  return <Link to={to} className={`quick-action ${tone}`}><span className="quick-icon"><Icon size={16} strokeWidth={1.9} /></span><span>{title}</span><ArrowRight className="quick-arrow" size={14} /></Link>;
}
