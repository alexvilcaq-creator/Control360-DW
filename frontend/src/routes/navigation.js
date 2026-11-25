import { Boxes, CalendarCheck, ChartColumnIncreasing, Clock3, LayoutDashboard, MapPin, Settings, ShieldCheck, ShoppingCart, Tags, Users, Warehouse } from 'lucide-react';

const all = ['ADMIN', 'VENDEDOR', 'ALMACEN'];
export const navigation = [
  { to: '/', label: 'Resumen', icon: LayoutDashboard, roles: all },
  { to: '/pedidos', label: 'Pedidos', icon: ShoppingCart, roles: all },
  { to: '/inventario', label: 'Inventario', icon: Boxes, roles: all },
  { to: '/almacen', label: 'Almacén', icon: Warehouse, roles: all },
  { to: '/trabajadores', label: 'Trabajadores', icon: Users, roles: ['ADMIN'] },
  { to: '/asistencia', label: 'Asistencia', icon: CalendarCheck, roles: all },
  { to: '/categorias', label: 'Categorías', icon: Tags, roles: ['ADMIN'], admin: true },
  { to: '/ubicaciones', label: 'Ubicaciones', icon: MapPin, roles: ['ADMIN'], admin: true },
  { to: '/turnos', label: 'Turnos', icon: Clock3, roles: ['ADMIN'], admin: true },
  { to: '/configuracion/usuarios', label: 'Usuarios', icon: ShieldCheck, roles: ['ADMIN'], admin: true },
  { to: '/reportes', label: 'Reportes', icon: ChartColumnIncreasing, roles: ['ADMIN'], admin: true },
];
export const settings = { to: '/configuracion', icon: Settings, label: 'Configuración', roles: all };
