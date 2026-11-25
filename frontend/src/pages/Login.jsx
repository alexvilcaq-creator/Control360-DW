import { useState } from 'react';
import { ArrowRight, Eye, EyeOff, LockKeyhole, UserRound } from 'lucide-react';
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
      <img className="login-photo" src="/images/warehouse-login.webp" alt="" fetchPriority="high" />
      <Brand />
      <div className="login-intro">
        <h1>Tu tienda y almacén en un solo lugar.</h1>
        <p>Gestiona pedidos, consulta existencias y coordina al equipo desde tu espacio de trabajo.</p>
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
          <Field label="Usuario" inputId="login-username">
            <div className="login-input"><UserRound size={18} aria-hidden="true" /><input id="login-username" name="username" autoComplete="username" placeholder="Tu usuario" required maxLength={80} /></div>
          </Field>
          <Field label="Contraseña" inputId="login-password">
            <div className="login-input password-field">
              <LockKeyhole size={17} aria-hidden="true" />
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
