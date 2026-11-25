import { useState } from 'react';
import { ArrowRight, ChartNoAxesColumnIncreasing, Eye, EyeOff, Layers3, LockKeyhole, PackageCheck, Warehouse } from 'lucide-react';
import { Brand, Button, ErrorNotice, Field } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { errorMessage } from '../services/api';

export default function Login() {
  const { login } = useAuth();
  const [busy, setBusy] = useState(false);
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    setBusy(true); setError('');
    try { await login(fields.get('username'), fields.get('password')); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <main className="login-shell">
    <section className="login-side">
      <Brand />
      <div className="login-intro">
        <span className="login-emblem"><Warehouse size={42} strokeWidth={1.45} /></span>
        <h1>Tu tienda y almacén en un solo lugar.</h1>
        <p>Gestiona pedidos, consulta existencias y coordina al equipo desde tu espacio de trabajo.</p>
        <div className="login-features"><span><PackageCheck size={18} />Pedidos</span><span><Layers3 size={18} />Inventario</span><span><ChartNoAxesColumnIncreasing size={18} />Reportes</span></div>
      </div>
      <div className="login-course">Desarrollo de Aplicaciones Web</div>
    </section>
    <section className="login-main">
      <div className="login-form">
        <span className="login-lock"><LockKeyhole size={24} /></span>
        <h2>Inicia sesión</h2>
        <p>Ingresa tus credenciales para acceder a Control360.</p>
        <form onSubmit={submit}>
          <ErrorNotice message={error} />
          <Field label="Usuario"><input name="username" autoComplete="username" placeholder="Tu usuario" required maxLength={80} /></Field>
          <Field label="Contraseña" inputId="login-password">
            <div className="password-field">
              <input id="login-password" name="password" type={visible ? 'text' : 'password'} autoComplete="current-password" placeholder="Tu contraseña" required maxLength={72} />
              <button type="button" className="icon-button" aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={18} /> : <Eye size={18} />}</button>
            </div>
          </Field>
          <Button type="submit" icon={ArrowRight} disabled={busy}>{busy ? 'Ingresando…' : 'Ingresar al panel'}</Button>
        </form>
      </div>
    </section>
  </main>;
}
