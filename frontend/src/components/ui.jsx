import { cloneElement, useEffect, useId, useRef, useState } from 'react';
import { AlertCircle, ArrowRight, Box, Inbox, LoaderCircle, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../services/api';
import { statusName, statusTone } from '../services/format';

export function Brand() {
  return <div className="brand">
    <span className="brand-mark"><Box size={26} strokeWidth={2.2} /></span>
    <div><strong>Control<span>360</span></strong><small>Gestión de operaciones</small></div>
  </div>;
}

export function Button({ icon: Icon, variant = 'primary', children, className = '', ...props }) {
  return <button type="button" className={`button ${variant} ${className}`} {...props}>{Icon && <Icon size={17} />}{children}</button>;
}

export function Tag({ tone = 'muted', children }) {
  return <span className={`tag ${tone}`}>{children}</span>;
}
export function Status({ value }) { return <Tag tone={statusTone[value]}>{statusName[value] || value}</Tag>; }

export function PageTitle({ title, subtitle, children }) {
  return <div className="page-title">
    <div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    {children && <div className="page-actions">{children}</div>}
  </div>;
}
export function Section({ title, icon: Icon, caption, action, children, className = '' }) {
  return <section className={`section ${className}`}>
    <header className="section-header"><div className="section-title">{Icon && <Icon size={20} />}
      <div><h2>{title}</h2>{caption && <p>{caption}</p>}</div>
    </div>{action}</header>
    {children}
  </section>;
}

export function TextLink({ to, children }) {
  return <Link to={to} className="text-link">{children}<ArrowRight size={15} /></Link>;
}

export function Loading() { return <div className="empty"><LoaderCircle className="spinner" size={24} /><p>Cargando datos…</p></div>; }
export function Empty({ children = 'No hay registros para mostrar.' }) { return <div className="empty"><Inbox size={26} /><p>{children}</p></div>; }
export function ErrorNotice({ message, retry }) {
  if (!message) return null;
  return <div className="error-notice" role="alert"><AlertCircle size={18} /><span>{message}</span>{retry && <Button variant="quiet" onClick={retry}>Reintentar</Button>}</div>;
}

export function DataTable({ columns, rows, empty, onClick }) {
  if (!rows?.length) return <Empty>{empty}</Empty>;
  return <div className="table-scroll"><table>
    <thead><tr>{columns.map((col) => <th key={col.key} className={col.align === 'right' ? 'right' : undefined}>{col.label}</th>)}</tr></thead>
    <tbody>{rows.map((row) => <tr key={row.id ?? row.order?.id ?? row.worker?.id} onClick={onClick ? () => onClick(row) : undefined}>
      {columns.map((col) => <td key={col.key} className={col.align === 'right' ? 'right' : undefined}>{col.render ? col.render(row) : row[col.key]}</td>)}
    </tr>)}</tbody>
  </table></div>;
}

export function Dialog({ title, children, onClose, wide = false }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog.showModal();
    return () => { if (dialog.open) dialog.close(); };
  }, []);
  return <dialog ref={ref} className={wide ? 'dialog wide' : 'dialog'} onCancel={(event) => { event.preventDefault(); onClose(); }}>
    <div className="dialog-heading"><h2>{title}</h2><button type="button" className="icon-button" aria-label="Cerrar ventana" onClick={onClose}><X size={20} /></button></div>
    <div className="dialog-body">{children}</div>
  </dialog>;
}

export function Field({ label, children, hint, inputId }) {
  const uid = useId();
  const id = inputId || uid;
  const hintId = uid + '-hint';
  return <div className="field"><label htmlFor={id}>{label}</label>
    {inputId ? children : cloneElement(children, { id, 'aria-describedby': hint ? hintId : undefined })}
    {hint && <small id={hintId}>{hint}</small>}
  </div>;
}
export function FormSubmit({ onSubmit, onClose, children, label = 'Guardar', disabled = false }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault();
    if (busy || disabled) return;
    setBusy(true); setError('');
    try { await onSubmit(new FormData(event.currentTarget)); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <form onSubmit={submit}>
    <ErrorNotice message={error} />
    {children}
    <div className="dialog-actions">
      <Button variant="outline" onClick={onClose}>Cancelar</Button>
      <Button type="submit" disabled={busy || disabled} icon={busy ? LoaderCircle : undefined}>{busy ? 'Guardando…' : label}</Button>
    </div>
  </form>;
}
