import { useState } from 'react';
import { AlertTriangle, BarChart3, CalendarDays, Clock3, Package, ShoppingCart, TrendingUp, Users } from 'lucide-react';
import { BarPanel, DateChart } from '../components/charts';
import { DataTable, ErrorNotice, Field, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useResource } from '../hooks/useResource';
import { hours, statusName } from '../services/format';

function initialDate(today) {
  const date = new Date(today + 'T12:00:00Z');
  date.setUTCDate(date.getUTCDate() - 6);
  return date.toISOString().slice(0, 10);
}

export default function Reports() {
  const calendar = useResource('/calendario');
  const [range, setRange] = useState({ from: '', to: '' });
  const today = calendar.data?.today;
  const end = range.to || today || '';
  const start = range.from || (end ? initialDate(end) : '');
  const reports = useResource(start && end ? '/reportes/resumen' : null, { from: start, to: end });
  const data = reports.data;
  return <>
    <PageTitle title="Reportes" subtitle="Analiza pedidos, existencias y asistencia del equipo.">
      <div className="inline-dates">
        <Field label="Desde"><input type="date" value={start} max={end || undefined} onChange={(event) => setRange({ ...range, from: event.target.value })} /></Field>
        <Field label="Hasta"><input type="date" value={end} min={start || undefined} max={today} onChange={(event) => setRange({ ...range, to: event.target.value })} /></Field>
      </div>
    </PageTitle>
    <ErrorNotice message={reports.error || calendar.error} retry={reports.reload} />
    {!data && (calendar.loading || reports.loading) ? <Loading /> : data && <>
      <div className="mini-metrics">
        <div><ShoppingCart size={21} /><span>Pedidos en el período</span><strong>{data.ordersToday}</strong></div>
        <div><Users size={21} /><span>Trabajadores activos</span><strong>{data.activeWorkers}</strong></div>
        <div><AlertTriangle size={21} /><span>Productos con stock bajo</span><strong>{data.lowStockCount}</strong></div>
        <div><Clock3 size={21} /><span>Horas extra aprobadas</span><strong>{hours(data.attendance.approvedMinutes)}</strong></div>
      </div>
      <div className="report-grid">
        <Section title="Pedidos por fecha" icon={TrendingUp}><DateChart data={data.ordersByDate} /></Section>
        <Section title="Pedidos por estado" icon={BarChart3}><BarPanel data={data.ordersByStatus.map((item) => ({ ...item, name: statusName[item.name] }))} unit="pedidos" height={245} horizontal /></Section>
        <Section title="Productos más solicitados" icon={Package}><BarPanel data={data.popularProducts} height={250} horizontal /></Section>
        <Section title="Asistencia del período" icon={CalendarDays} action={<TextLink to="/asistencia">Ver historial</TextLink>}>
          <BarPanel data={[
            { name: 'Turnos programados', value: data.attendance.scheduled },
            { name: 'Asistencias', value: data.attendance.present },
            { name: 'Tardanzas', value: data.attendance.late },
            { name: 'Faltas', value: data.attendance.absent },
          ]} unit="registros" height={250} horizontal />
        </Section>
        <Section title="Productos con stock bajo" icon={AlertTriangle} className="report-full" action={<TextLink to="/inventario?stock=bajo">Ver todos</TextLink>}>
          <DataTable rows={data.lowStock} empty="No hay productos con stock bajo." columns={[
            { key: 'sku', label: 'SKU' }, { key: 'name', label: 'Producto' },
            { key: 'minStock', label: 'Mínimo', align: 'right' },
            { key: 'quantity', label: 'Existencia', align: 'right', render: (item) => <Tag tone="red">{item.quantity} u.</Tag> },
          ]} />
        </Section>
      </div>
    </>}
  </>;
}
