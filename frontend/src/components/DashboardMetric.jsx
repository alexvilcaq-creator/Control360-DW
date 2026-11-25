import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function DashboardMetric({ icon: Icon, title, value, label, tone, to }) {
  return <Link to={to} className={`metric ${tone}`}>
    <div className="metric-head"><span className="metric-icon"><Icon size={18} strokeWidth={1.9} /></span><p>{title}</p><ArrowRight size={15} className="metric-arrow" /></div>
    <strong>{value}</strong><small>{label}</small>
  </Link>;
}
