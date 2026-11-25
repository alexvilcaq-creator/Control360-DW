import { CircleUserRound, Moon, Shield, Sun, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import Records from '../components/Records';
import { Button, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useResource } from '../hooks/useResource';
import { fullName, roleName } from '../services/format';

export function Settings() {
  const { user } = useAuth();
  const { theme, setTheme } = useTheme();
  return <>
    <PageTitle title="Configuración" subtitle="Consulta tu cuenta y ajusta la apariencia de tu espacio de trabajo." />
    <div className="report-grid">
      <Section title="Mi perfil" icon={CircleUserRound}>
        <div className="settings-card"><span className="avatar">{user.username.slice(0, 2).toUpperCase()}</span>
          <div><strong>{user.username}</strong><small>{roleName[user.role]}</small></div><Tag tone="green">Activo</Tag></div>
      </Section>
      <Section title="Apariencia" icon={Sun}>
        <div className="settings-card segmented">
          <Button variant={theme === 'light' ? 'primary' : 'outline'} icon={Sun} onClick={() => setTheme('light')}>Modo claro</Button>
          <Button variant={theme === 'dark' ? 'primary' : 'outline'} icon={Moon} onClick={() => setTheme('dark')}>Modo oscuro</Button>
        </div>
      </Section>
      {user.role === 'ADMIN' && <Section title="Acceso de usuarios" icon={Shield} caption="Crea cuentas, asigna roles y administra el acceso." action={<TextLink to="/configuracion/usuarios">Gestionar usuarios</TextLink>} />}
    </div>
  </>;
}
export function UsersPage() {
  const workers = useResource('/trabajadores');
  return <Records title="Usuarios" subtitle="Administra cuentas, roles y vínculos con trabajadores." singular="usuario" icon={Users}
    dependencies={[workers]} endpoint="/usuarios" searchBy={(item) => item.username + ' ' + roleName[item.role]}
    fields={[
      { name: 'username', label: 'Usuario', required: true, pattern: String.raw`[A-Za-z0-9_.\-]{3,80}`, maxLength: 80 },
      { name: 'password', label: 'Contraseña', type: 'password', minLength: 12, maxLength: 72, requiredOnCreate: true, hint: 'Al editar, deja este campo vacío para conservar la contraseña.' },
      { name: 'role', label: 'Rol', required: true, options: Object.entries(roleName).map(([value, label]) => ({ value, label })) },
      { name: 'workerId', label: 'Trabajador vinculado', hint: 'Vincula la ficha del vendedor o del operario para habilitar su asistencia.', numeric: true, options: (workers.data || []).map(({ worker }) => ({ value: worker.id, label: fullName(worker) })) },
      { name: 'active', label: 'Cuenta activa', type: 'checkbox', defaultValue: true },
    ]}
    columns={[
      { key: 'username', label: 'Usuario' },
      { key: 'role', label: 'Rol', render: (item) => <Tag tone="blue">{roleName[item.role]}</Tag> },
      { key: 'worker', label: 'Trabajador', render: (item) => fullName(workers.data?.find(({ worker }) => worker.id === item.workerId)?.worker) },
      { key: 'active', label: 'Estado', render: (item) => <Tag tone={item.active ? 'green' : 'muted'}>{item.active ? 'Activo' : 'Inactivo'}</Tag> },
    ]}><p className="back-link"><Link to="/configuracion">Volver a configuración</Link></p></Records>;
}
