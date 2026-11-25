export const roleName = { ADMIN: 'Administrador', VENDEDOR: 'Vendedor', ALMACEN: 'Trabajador de almacén' };
export const statusName = {
  PENDIENTE: 'Pendiente', ACEPTADO: 'Aceptado', EN_PREPARACION: 'En preparación',
  LISTO: 'Listo para entregar', ENTREGADO: 'Entregado', CANCELADO: 'Cancelado',
};
export const statusTone = {
  PENDIENTE: 'amber', ACEPTADO: 'purple', EN_PREPARACION: 'blue',
  LISTO: 'teal', ENTREGADO: 'green', CANCELADO: 'red',
};
const moneyFormatter = Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' });
const dateFormatter = Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric' });
const timeFormatter = Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', hour12: false });
export const money = (amount) => moneyFormatter.format(amount || 0);
export const dateLabel = (value) => value ? dateFormatter.format(new Date(value.length === 10 ? value + 'T12:00:00' : value)) : '—';
export const timeLabel = (value) => value ? timeFormatter.format(new Date(value)) : '—';
export const orderCode = (id) => 'PED-' + String(id).padStart(4, '0');
export const locationLabel = (location) => location ? `Zona ${location.zone} · Pasillo ${location.aisle} · Estante ${location.shelf}` : '—';
export const fullName = (worker) => worker ? worker.firstName + ' ' + worker.lastName : '—';
export const normalize = (value) => String(value ?? '').toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
export const aisleCode = ({ zone, aisle }) => `${zone}${aisle}`;
export const shelfCode = (location) => `${aisleCode(location)} · E${location.shelf}`;

export function matchesLocation(location, query) {
  const term = normalize(query).trim();
  const compact = (value) => normalize(value).replace(/[\s.·-]+/g, '');
  const code = compact(term);
  return normalize(locationLabel(location)).includes(term)
    || Boolean(code && [aisleCode(location), shelfCode(location)].some((alias) => compact(alias) === code));
}

export const hours = (minutes) => Number((minutes / 60).toFixed(1)).toLocaleString('es-PE') + ' h';
