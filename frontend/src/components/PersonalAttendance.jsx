import { CalendarCheck, Clock3 } from 'lucide-react';
import { ErrorNotice, Loading, Section, Tag, TextLink } from './ui';
import { dateLabel, timeLabel } from '../services/format';

export default function PersonalAttendance({ resource, showHistory = true }) {
  const state = resource.data;
  const open = state?.open;
  const latest = state?.latest;
  const markedToday = latest && (latest.workDate === state.today || latest.exit?.slice(0, 10) === state.today);
  return <Section title="Mi asistencia" icon={CalendarCheck} className="personal-attendance"
    action={showHistory && <TextLink to="/asistencia">Ver historial</TextLink>}>
    <ErrorNotice message={resource.error} retry={resource.reload} />
    {resource.loading && !state ? <Loading /> : state && (state.workerId == null
      ? <p className="muted personal-note">Pide al administrador que vincule tu cuenta a tu ficha de trabajador para registrar asistencia.</p>
      : <div className="personal-attendance-body">
        <div>
          <Tag tone={open ? 'green' : markedToday ? 'teal' : 'muted'} dot>
            {open ? 'En jornada' : markedToday ? 'Jornada cerrada' : 'Sin marcación hoy'}
          </Tag>
          <p><Clock3 size={14} aria-hidden="true" /> {state.shiftName || 'Turno sin asignar'}</p>
          {(open || latest) && <small>{open ? `Entrada: ${dateLabel(open.entry)} · ${timeLabel(open.entry)}`
            : `Última salida: ${dateLabel(latest.exit)} · ${timeLabel(latest.exit)}`}</small>}
        </div>
        {(open || (!markedToday && state.shiftName)) && <TextLink to={`/asistencia?marcar=1&tipo=${open ? 'salidas' : 'entradas'}`}>
          {open ? 'Marcar salida' : 'Marcar entrada'}
        </TextLink>}
      </div>)}
  </Section>;
}
