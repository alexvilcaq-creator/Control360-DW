import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Empty } from './ui';

const palette = ['#3a70ee', '#2abc91', '#7ba1fa', '#75d6b6', '#3972ed', '#25b88e'];

function ChartTooltip({ active, payload, label, unit }) {
  if (!active || !payload?.length) return null;
  return <div className="chart-tooltip"><strong>{label}</strong><span>{payload[0].value.toLocaleString('es-PE')} {unit}</span></div>;
}

export function BarPanel({ data, unit = 'unidades', height = 212, horizontal = false }) {
  if (!data?.length) return <Empty>No hay datos para graficar.</Empty>;
  return <div className="chart" style={{ height }} role="img" aria-label={data.map((item) => `${item.name}: ${item.value} ${unit}`).join(', ')}>
    <ResponsiveContainer width="100%" height="100%" minWidth={0}>
      <BarChart data={data} layout={horizontal ? 'vertical' : 'horizontal'} margin={{ top: 21, right: 14, bottom: 3, left: horizontal ? 0 : -27 }}>
        <CartesianGrid stroke="var(--line)" strokeDasharray="3 4" vertical={horizontal} horizontal={!horizontal} />
        <XAxis type={horizontal ? 'number' : 'category'} dataKey={horizontal ? undefined : 'name'} tickLine={false} axisLine={false} tick={{ fill: 'var(--muted)', fontSize: 11 }} tickMargin={9} allowDecimals={false} />
        <YAxis type={horizontal ? 'category' : 'number'} dataKey={horizontal ? 'name' : undefined} width={horizontal ? 112 : 60} tickLine={false} axisLine={false} tick={{ fill: 'var(--muted)', fontSize: 11 }} allowDecimals={false} />
        <Tooltip cursor={{ fill: 'var(--chart-hover)' }} content={<ChartTooltip unit={unit} />} />
        <Bar dataKey="value" radius={horizontal ? [0, 5, 5, 0] : [6, 6, 0, 0]} maxBarSize={53} isAnimationActive={false}>
          {data.map((entry, index) => <Cell key={entry.name} fill={palette[index % palette.length]} />)}
          {!horizontal && <LabelList dataKey="value" position="top" style={{ fill: 'var(--ink)', fontSize: 11, fontWeight: 670 }} />}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  </div>;
}

export function DateChart({ data }) {
  if (!data?.length) return <Empty>No hay datos para graficar.</Empty>;
  return <div className="chart" style={{ height: 245 }} role="img" aria-label="Evolución de pedidos por fecha">
    <ResponsiveContainer width="100%" height="100%" minWidth={0}>
      <AreaChart data={data} margin={{ top: 12, bottom: 5, left: -25, right: 18 }}>
        <defs><linearGradient id="order-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#477af1" stopOpacity={0.25} /><stop offset="100%" stopColor="#477af1" stopOpacity={0.02} /></linearGradient></defs>
        <CartesianGrid stroke="var(--line)" strokeDasharray="3 4" vertical={false} />
        <XAxis dataKey="name" tickLine={false} axisLine={false} minTickGap={24} tick={{ fill: 'var(--muted)', fontSize: 11 }} tickFormatter={(date) => date.substring(8) + '/' + date.substring(5, 7)} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: 'var(--muted)', fontSize: 11 }} />
        <Tooltip content={<ChartTooltip unit="pedidos" />} />
        <Area type="monotone" dataKey="value" stroke="#477af1" strokeWidth={2.8} fill="url(#order-fill)" isAnimationActive={false} />
      </AreaChart>
    </ResponsiveContainer>
  </div>;
}
