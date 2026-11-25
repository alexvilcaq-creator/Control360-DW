import { Clock3, MapPin, Tags, Users } from 'lucide-react';
import Records from '../components/Records';
import { Tag } from '../components/ui';
import { useResource } from '../hooks/useResource';
import { fullName, locationLabel } from '../services/format';

const textField = (name, label, maxLength = 100) => ({ name, label, required: true, maxLength });
const dayNames = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

export function Categories() {
  return <Records title="Categorías" subtitle="Organiza tus productos por familia." singular="categoría" icon={Tags}
    endpoint="/categorias" fields={[textField('name', 'Nombre de la categoría')]} searchBy={(item) => item.name}
    columns={[{ key: 'name', label: 'Nombre' }]} />;
}
export function Locations() {
  return <Records title="Ubicaciones" subtitle="Define las zonas, pasillos y estantes del almacén." singular="ubicación" icon={MapPin}
    endpoint="/ubicaciones" fields={[textField('zone', 'Zona', 20), textField('aisle', 'Pasillo', 20), textField('shelf', 'Estante', 20)]}
    searchBy={locationLabel} columns={[{ key: 'zone', label: 'Zona' }, { key: 'aisle', label: 'Pasillo' }, { key: 'shelf', label: 'Estante' }]} />;
}
export function Shifts() {
  return <Records title="Turnos" subtitle="Configura el horario de trabajo y la tolerancia de entrada." singular="turno" icon={Clock3}
    endpoint="/turnos" searchBy={(item) => item.name} fields={[
      textField('name', 'Nombre del turno'),
      { name: 'startsAt', label: 'Hora de entrada', type: 'time', required: true, defaultValue: '08:00' },
      { name: 'endsAt', label: 'Hora de salida', type: 'time', required: true, defaultValue: '17:00' },
      { name: 'graceMinutes', label: 'Tolerancia (minutos)', type: 'number', min: 0, max: 180, required: true, defaultValue: 10 },
      { ...textField('days', 'Días del turno', 13), defaultValue: '1,2,3,4,5', hint: '1 es lunes y 7 es domingo. Separa los días con comas.', pattern: '[1-7](,[1-7]){0,6}' },
    ]} columns={[
      { key: 'name', label: 'Turno' },
      { key: 'days', label: 'Días', render: (item) => item.days.split(',').map((day) => dayNames[Number(day) - 1]).join(', ') },
      { key: 'startsAt', label: 'Entrada', render: (item) => item.startsAt.slice(0, 5) },
      { key: 'endsAt', label: 'Salida', render: (item) => item.endsAt.slice(0, 5) },
      { key: 'graceMinutes', label: 'Tolerancia', render: (item) => item.graceMinutes + ' min' },
    ]} />;
}
export function Workers() {
  const shifts = useResource('/turnos');
  const shiftName = Object.fromEntries((shifts.data || []).map((item) => [item.id, item.name]));
  return <Records title="Trabajadores" subtitle="Gestiona el equipo y consulta su disponibilidad según el turno asignado." singular="trabajador" icon={Users}
    endpoint="/trabajadores" dependencies={[shifts]} unpack={(item) => item.worker}
    searchBy={({ worker }) => fullName(worker) + ' ' + worker.email + ' ' + worker.document}
    fields={[
      textField('firstName', 'Nombres'), textField('lastName', 'Apellidos'), textField('document', 'Documento', 30),
      { ...textField('email', 'Correo electrónico', 180), type: 'email' }, textField('position', 'Cargo'),
      { name: 'shiftId', label: 'Turno', numeric: true, options: (shifts.data || []).map((item) => ({ value: item.id, label: item.name })) },
      { name: 'active', label: 'Trabajador activo', type: 'checkbox', defaultValue: true },
    ]} columns={[
      { key: 'person', label: 'Trabajador', render: ({ worker }) => <div className="entity-cell"><span className="avatar small">{(worker.firstName[0] + worker.lastName[0]).toUpperCase()}</span><div><strong>{fullName(worker)}</strong><small>{worker.email}</small></div></div> },
      { key: 'document', label: 'Documento', render: ({ worker }) => worker.document },
      { key: 'position', label: 'Cargo', render: ({ worker }) => worker.position },
      { key: 'shift', label: 'Turno', render: ({ worker }) => shiftName[worker.shiftId] || 'Sin turno' },
      { key: 'available', label: 'Disponibilidad', render: ({ worker, available }) => <Tag tone={available ? 'green' : 'muted'}>{available ? 'Disponible' : worker.active ? 'Fuera de turno' : 'Inactivo'}</Tag> },
    ]} />;
}
