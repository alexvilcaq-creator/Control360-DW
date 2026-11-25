import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { DataTable, Status } from './ui';
import { dateLabel, money, orderCode, timeLabel } from '../services/format';

export default function OrderTable({ rows, empty }) {
  return <DataTable rows={rows} empty={empty ?? 'No hay pedidos para esta fecha.'} columns={[
    { key: 'number', label: 'N.º pedido', render: ({ order }) => <Link className="table-link" to={`/pedidos?pedido=${order.id}`}>{orderCode(order.id)}</Link> },
    { key: 'customer', label: 'Cliente' },
    { key: 'date', label: 'Fecha y hora', render: ({ order }) => <span className="muted">{dateLabel(order.createdAt)}<span className="date-time"> · {timeLabel(order.createdAt)}</span></span> },
    { key: 'status', label: 'Estado', render: ({ order }) => <Status value={order.status} /> },
    { key: 'total', label: 'Total', align: 'right', render: ({ order }) => <strong>{money(order.total)}</strong> },
    { key: 'detail', label: '', align: 'right', render: ({ order }) => <Link className="table-detail" aria-label={`Ver ${orderCode(order.id)}`} to={`/pedidos?pedido=${order.id}`}><ArrowUpRight size={16} /></Link> },
  ]} />;
}
