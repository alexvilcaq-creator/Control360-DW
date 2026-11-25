import { useState } from 'react';
import { CalendarCheck, Clock3, LogIn, LogOut, UserRoundCheck, UserRoundX } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { Button, DataTable, Dialog, ErrorNotice, Field, FormSubmit, Loading, PageTitle, Section, Tag } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { post, put } from '../services/api';
import { dateLabel, fullName, hours, timeLabel } from '../services/format';

export default function Attendance() {
  const { user } = useAuth();
  const admin = user.role === 'ADMIN';
  const [params, setParams] = useSearchParams();
  const [range, setRange] = useState({ from: '', to: '' });
  const [editing, setEditing] = useState(null);
  const workers = useResource(admin ? '/trabajadores' : null);
  const calendar = useResource('/calendario');
  const dates = { from: range.from || undefined, to: range.to || undefined };
  const history = useResource('/asistencias', dates);
  const absences = useResource('/asistencias/faltas', dates);
  const [workerFilter, setWorkerFilter] = useState('');
  const rows = history.data?.filter((item) => !workerFilter || item.workerId === Number(workerFilter));
  const absenceRows = absences.data?.filter((item) => !workerFilter || item.workerId === Number(workerFilter))
    .map((item) => ({ ...item, id: item.workerId + '/' + item.workDate }));
  const today = calendar.data?.today;
  const visible = (history.data || []).filter((item) => item.workDate === today);
  const who = Object.fromEntries((workers.data || []).map(({ worker }) => [worker.id, fullName(worker)]));

  const columns = [
    { key: 'date', label: 'Fecha', render: (item) => dateLabel(item.workDate) },
    { key: 'entry', label: 'Entrada', render: (item) => timeLabel(item.entry) },
    { key: 'exit', label: 'Salida', render: (item) => item.exit ? timeLabel(item.exit) : <Tag tone="blue">En jornada</Tag> },
    { key: 'late', label: 'Estado', render: (item) => item.lateMinutes ? <Tag tone="amber" dot>Tardanza · {item.lateMinutes} min</Tag> : <Tag tone="green" dot>Presente</Tag> },
    { key: 'extra', label: 'Tiempo adicional', render: (item) => item.additionalMinutes ? `${item.additionalMinutes} min` : '—' },
    { key: 'approved', label: 'Extra aprobado', render: (item) => item.approvedMinutes ? <Tag tone="green">{item.approvedMinutes} min</Tag> : '—' },
  ];
  if (admin) {
    columns.unshift({ key: 'worker', label: 'Trabajador', render: (item) => who[item.workerId] || `#${item.workerId}` });
    columns.push({ key: 'actions', label: '', align: 'right', render: (item) => item.exit && item.additionalMinutes > 0
      ? <Button variant="quiet" onClick={() => setEditing(item)}>{item.approvedMinutes ? 'Revisar' : 'Aprobar'}</Button> : null });
  }

  function markClose() { const next = new URLSearchParams(params); next.delete('marcar'); setParams(next); }
  function saved() { markClose(); setEditing(null); history.reload(); absences.reload(); }

  return <>
    <PageTitle title="Asistencia" subtitle="Registra entradas y salidas, y consulta tardanzas y horas extra aprobadas.">
      <Button icon={CalendarCheck} onClick={() => setParams({ marcar: 1 })}>Marcar asistencia</Button>
    </PageTitle>
    <ErrorNotice message={history.error || absences.error || workers.error || calendar.error} retry={history.reload} />
    <div className="mini-metrics">
      <div><UserRoundCheck size={20} /><span>Registros de hoy</span><strong>{visible.length}</strong></div>
      <div><Clock3 size={20} /><span>Tardanzas en la selección</span><strong>{rows?.filter((item) => item.lateMinutes > 0).length || 0}</strong></div>
      <div><UserRoundX size={20} /><span>Faltas en la selección</span><strong>{absenceRows?.length || 0}</strong></div>
      <div><CalendarCheck size={20} /><span>Horas extra aprobadas</span><strong>{hours(rows?.reduce((total, item) => total + item.approvedMinutes, 0) || 0)}</strong></div>
    </div>
    <Section title="Historial de asistencia" className="table-panel" icon={CalendarCheck}>
      <div className="filters dates-filter">
        <Field label="Desde"><input type="date" value={range.from} max={range.to || today} onChange={(event) => setRange({ ...range, from: event.target.value })} /></Field>
        <Field label="Hasta"><input type="date" value={range.to} min={range.from || undefined} max={today} onChange={(event) => setRange({ ...range, to: event.target.value })} /></Field>
        {admin && <Field label="Trabajador"><select value={workerFilter} onChange={(event) => setWorkerFilter(event.target.value)}><option value="">Todos los trabajadores</option>
          {workers.data?.map(({ worker }) => <option key={worker.id} value={worker.id}>{fullName(worker)}</option>)}</select></Field>}
      </div>
      {history.loading && !history.data ? <Loading /> : <DataTable rows={rows} columns={columns} empty="No hay marcaciones para esta selección." />}
    </Section>
    <div className="section-gap">
      <Section title="Faltas del período" className="table-panel" icon={CalendarCheck}>
        {absences.loading && !absences.data ? <Loading /> : <DataTable
          rows={absenceRows}
          columns={[{ key: 'worker', label: 'Trabajador' }, { key: 'workDate', label: 'Fecha', render: (item) => dateLabel(item.workDate) }, { key: 'shift', label: 'Turno' },
            { key: 'state', label: 'Estado', render: () => <Tag tone="red" dot>Falta</Tag> }]}
          empty="No hay faltas para esta selección." />}
      </Section>
    </div>
    {params.get('marcar') && <MarkDialog admin={admin} workers={workers.data || []} close={markClose} saved={saved} />}
    {editing && <Dialog title="Aprobar tiempo extra" onClose={() => setEditing(null)}>
      <p className="form-note">{who[editing.workerId]} · {dateLabel(editing.workDate)}. Se registraron {editing.additionalMinutes} minutos adicionales.</p>
      <FormSubmit onClose={() => setEditing(null)} onSubmit={async (values) => {
        await put(`/asistencias/${editing.id}/aprobar`, { minutes: Number(values.get('minutes')) }); saved();
      }}><Field label="Minutos aprobados"><input name="minutes" type="number" min={0} max={editing.additionalMinutes} defaultValue={editing.approvedMinutes || editing.additionalMinutes} required /></Field></FormSubmit>
    </Dialog>}
  </>;
}

function MarkDialog({ admin, workers, close, saved }) {
  const [kind, setKind] = useState('entradas');
  return <Dialog title="Marcar asistencia" onClose={close}>
    <div className="segmented">
      <Button icon={LogIn} variant={kind === 'entradas' ? 'primary' : 'outline'} onClick={() => setKind('entradas')}>Entrada</Button>
      <Button icon={LogOut} variant={kind === 'salidas' ? 'primary' : 'outline'} onClick={() => setKind('salidas')}>Salida</Button>
    </div>
    <FormSubmit onClose={close} label={kind === 'entradas' ? 'Registrar entrada' : 'Registrar salida'} onSubmit={async (values) => {
      await post(`/asistencias/${kind}`, admin ? { workerId: Number(values.get('workerId')) } : {}); saved();
    }}>
      {admin ? <Field label="Trabajador"><select name="workerId" required><option value="">Selecciona un trabajador</option>
        {workers.filter(({ worker }) => kind === 'salidas' || worker.active).map(({ worker }) => <option key={worker.id} value={worker.id}>{fullName(worker)}</option>)}</select></Field>
        : <p className="form-note">La fecha y hora se registran automáticamente.</p>}
    </FormSubmit>
  </Dialog>;
}
