import { useState } from 'react';
import { Activity, AlertTriangle, ArrowRight, Boxes, CalendarDays, ChartNoAxesColumnIncreasing, Clock3, Droplet, ListChecks, Package, PackagePlus, Plus, ShoppingCart, Users, UserRoundCheck, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import { BarPanel } from '../components/charts';
import OrderTable from '../components/OrderTable';
import { Empty, ErrorNotice, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { dateLabel, hours, money } from '../services/format';

export default function Dashboard() {
  const { user } = useAuth();
  const calendar = useResource('/calendario');
  const [date, setDate] = useState('');
  const selected = date || calendar.data?.today;
  const [chartMode, setChartMode] = useState('quantity');
  const dashboard = useResource('/dashboard', selected ? { from: selected, to: selected } : {}, 25000);
  const categories = useResource('/categorias');
  const inventory = useResource(chartMode === 'value' ? '/productos' : null);
  const data = dashboard.data;
  const attendance = data?.attendance;
  const admin = user.role === 'ADMIN';
  const warehouse = user.role === 'ALMACEN';
  const seller = user.role === 'VENDEDOR';
  const rate = attendance?.scheduled ? Math.min(100, Math.round(100 * attendance.present / attendance.scheduled)) : 0;
  const categoryNames = Object.fromEntries((categories.data || []).map((item) => [item.id, item.name]));
  const chart = chartMode === 'quantity' ? data?.inventoryByCategory : (categories.data || []).map((category) => ({
    name: category.name,
    value: Math.round((inventory.data || []).filter((product) => product.active && product.categoryId === category.id)
      .reduce((total, product) => total + product.quantity * product.price, 0) * 100) / 100,
  }));

  return <>
    <PageTitle title="Resumen de operaciones" subtitle="Supervisa los pedidos, el inventario y la actividad de tu equipo.">
      <label className="date-control">
        <CalendarDays size={18} /><span>{selected === calendar.data?.today ? 'Hoy, ' : ''}{dateLabel(selected)}</span>
        <input type="date" aria-label="Fecha de resumen" value={selected || ''} max={calendar.data?.today} onChange={(event) => setDate(event.target.value)} />
      </label>
    </PageTitle>
    <ErrorNotice message={dashboard.error || calendar.error} retry={dashboard.reload} />
    {dashboard.loading && !data ? <Loading /> : data && <>
      <div className="metrics-grid">
        <Metric icon={ShoppingCart} tone="blue" title="Pedidos del día" value={data.ordersToday} label="Registrados en la fecha" to="/pedidos" />
        <Metric icon={Clock3} tone="amber" title="Pedidos pendientes" value={data.pendingOrders} label="Por aceptar en almacén" to="/pedidos?estado=PENDIENTE" />
        <Metric icon={Boxes} tone="purple" title="Productos con bajo stock" value={data.lowStockCount} label="Requieren atención" to="/inventario?stock=bajo" />
        {seller
          ? <Metric icon={Package} tone="green" title="Pedidos entregados" value={data.ordersByStatus.find((item) => item.name === 'ENTREGADO')?.value || 0} label="En la fecha seleccionada" to="/pedidos?estado=ENTREGADO" />
          : <Metric icon={Users} tone="green" title={selected === calendar.data?.today ? "Trabajadores presentes" : "Asistencia del personal"} value={`${selected === calendar.data?.today ? attendance.atWork : attendance.present} / ${attendance.scheduled}`} label={`${rate}% de asistencia`} to="/asistencia" />}
      </div>
      <div className="dashboard-grid dashboard-main">
        <Section title="Pedidos recientes" icon={ShoppingCart} action={<TextLink to="/pedidos">Ver todos</TextLink>} className="recent-panel">
          <OrderTable rows={data.recentOrders} />
        </Section>
        <Section title="Inventario por categoría" icon={ChartNoAxesColumnIncreasing} action={<select aria-label="Vista de inventario" className="mini-select" value={chartMode} onChange={(event) => setChartMode(event.target.value)}>
          <option value="quantity">Por cantidad</option><option value="value">Por valor</option>
        </select>}>
          {chartMode === 'value' && inventory.loading ? <Loading /> : <BarPanel data={chart} unit={chartMode === 'quantity' ? 'unidades' : 'soles'} />}
          <ErrorNotice message={inventory.error} />
        </Section>
      </div>
      <div className="dashboard-grid dashboard-secondary">
        <Section title="Alertas de bajo stock" icon={AlertTriangle} action={<TextLink to="/inventario?stock=bajo">Ver inventario</TextLink>} className="alerts-panel">
          {data.lowStock.length ? <div className="alert-list">{data.lowStock.slice(0, 3).map((product) =>
            <Link to={`/inventario?q=${encodeURIComponent(product.sku)}`} className="stock-alert" key={product.id}>
              <span className="product-tile">{/bebida|limpieza/i.test(categoryNames[product.categoryId] || '') ? <Droplet size={18} /> : <Package size={18} />}</span>
              <span className="stock-product"><strong>{product.name}</strong><small>{categoryNames[product.categoryId] || product.sku}</small></span>
              <Tag tone="red">{product.quantity} u.</Tag><span className="stock-min muted">Mín. {product.minStock}</span><ArrowRight size={15} className="stock-arrow" />
            </Link>)}</div> : <Empty>No hay productos por debajo del mínimo.</Empty>}
        </Section>
        <Section title="Acciones rápidas" icon={Zap}>
          <div className="quick-grid">
            {!warehouse && <Quick to="/pedidos?nuevo=1" icon={Plus} title="Registrar pedido" tone="blue" />}
            {admin && <Quick to="/inventario?nuevo=1" icon={PackagePlus} title="Ingresar producto" tone="green" />}
            {admin && <Quick to="/trabajadores" icon={Users} title="Gestionar equipo" tone="purple" />}
            {!seller && <Quick to="/asistencia?marcar=1" icon={UserRoundCheck} title="Marcar asistencia" tone="teal" />}
            {!admin && <Quick to={warehouse ? '/pedidos?estado=PENDIENTE' : '/pedidos?clientes=1'} icon={ListChecks} title={warehouse ? 'Ver pendientes' : 'Registrar cliente'} tone="green" />}
          </div>
        </Section>
        {!seller && <Section title="Asistencia" icon={Activity} className="attendance-panel">
          <div className="attendance-widget">
            <div className="donut" style={{ '--progress': Math.min(100, rate) + '%' }}><div><strong>{rate}%</strong><span>Asistencia</span></div></div>
            <div className="attendance-stats"><span><i /> Presentes <strong>{attendance.present}</strong></span>
              <span><i /> Faltas <strong>{attendance.absent}</strong></span>
              <span><i /> Horas extra <strong>{hours(attendance.approvedMinutes)}</strong></span></div>
          </div>
        </Section>}
      </div>
    </>}
  </>;
}

function Metric({ icon: Icon, title, value, label, tone, to }) {
  return <Link to={to} className="metric">
    <span className={`metric-icon ${tone}`}><Icon size={22} strokeWidth={2} /></span>
    <div><p>{title}</p><strong>{value}</strong><small>{label}</small></div>
    <ArrowRight size={16} className="metric-arrow" />
  </Link>;
}
function Quick({ to, icon: Icon, title, tone }) {
  return <Link to={to} className={`quick-action ${tone}`}><Icon size={23} strokeWidth={1.9} /><span>{title}</span></Link>;
}
