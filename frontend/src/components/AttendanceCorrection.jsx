import { DataTable, Dialog, ErrorNotice, Field, FormSubmit, Loading } from './ui';
import { useResource } from '../hooks/useResource';
import { put } from '../services/api';
import { dateLabel, timeLabel } from '../services/format';

function formTime(value, original) {
  if (!value) return null;
  const seconds = value.length === 16 ? value + ':00' : value;
  return seconds === original?.slice(0, 19) ? original : seconds;
}

function snapshot(value) {
  const stamp = (time) => time ? `${dateLabel(time)} · ${timeLabel(time)}` : 'Sin salida';
  return <div className="attendance-snapshot">
    <span>Entrada: {stamp(value.entry)}</span>
    <span>Salida: {stamp(value.exit)}</span>
    <small>Tardanza: {value.lateMinutes} min · Extra: {value.additionalMinutes} min · Aprobados: {value.approvedMinutes} min</small>
  </div>;
}

export default function AttendanceCorrection({ record, worker, close, saved }) {
  const endpoint = `/asistencias/${record.id}`;
  const history = useResource(endpoint + '/auditoria');
  return <Dialog title="Corregir asistencia" onClose={close} wide>
    <p className="form-note">{worker} · {dateLabel(record.workDate)}. Usa la hora del servidor de Control360.</p>
    <FormSubmit onClose={close} label="Guardar corrección" onSubmit={async (values) => {
      await put(endpoint + '/corregir', {
        entry: formTime(values.get('entry'), record.entry),
        exit: formTime(values.get('exit'), record.exit),
        reason: values.get('reason'),
      });
      saved();
    }}>
      <div className="form-grid">
        <Field label="Entrada"><input type="datetime-local" name="entry" step="1" defaultValue={record.entry.slice(0, 19)} required /></Field>
        <Field label="Salida" hint="Deja este campo vacío si la jornada sigue abierta.">
          <input type="datetime-local" name="exit" step="1" defaultValue={record.exit?.slice(0, 19) || ''} />
        </Field>
      </div>
      <Field label="Motivo de la corrección"><textarea name="reason" rows={2} maxLength={255} required /></Field>
    </FormSubmit>
    <div className="section-gap attendance-audit">
      <h3>Historial de correcciones y aprobaciones</h3>
      <ErrorNotice message={history.error} retry={history.reload} />
      {history.loading && !history.data ? <Loading /> : <DataTable rows={history.data} empty="Aún no hay cambios registrados."
        columns={[
          { key: 'action', label: 'Cambio', render: (item) => <div className="attendance-snapshot">
            <strong>{item.action === 'CORRECCION' ? 'Corrección' : 'Horas extra'}</strong>
            <span>{item.administratorName} · {dateLabel(item.createdAt)} · {timeLabel(item.createdAt)}</span>
            <small>{item.reason}</small>
          </div> },
          { key: 'before', label: 'Antes', render: (item) => snapshot(item.before) },
          { key: 'after', label: 'Después', render: (item) => snapshot(item.after) },
        ]} />}
    </div>
  </Dialog>;
}
