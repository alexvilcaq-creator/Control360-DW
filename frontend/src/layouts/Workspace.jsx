import { useEffect, useState } from 'react';
import { ArrowRight, Bell, CheckCheck, ChevronDown, CircleHelp, LogOut, Menu, Moon, Package2, Search, ShoppingCart, Sun, Users, X } from 'lucide-react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { Brand, Button, Dialog, Empty, ErrorNotice, Loading } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useResource } from '../hooks/useResource';
import { navigation, settings } from '../routes/navigation';
import { errorMessage, post } from '../services/api';
import { dateLabel, fullName, normalize, orderCode, roleName, timeLabel } from '../services/format';

export default function Workspace() {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const routerLocation = useLocation();
  const [search, setSearch] = useState(false);
  const [popover, setPopover] = useState('');
  const [menu, setMenu] = useState(false);
  const [error, setError] = useState('');
  const notifications = useResource('/notificaciones', {}, 20000);
  const unread = notifications.data?.filter((item) => !item.read).length || 0;
  const allowed = navigation.filter((item) => item.roles.includes(user.role));

  useEffect(() => {
    const keyboard = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault(); setSearch(true); setPopover('');
      }
      if (event.key === 'Escape') { setPopover(''); setMenu(false); }
    };
    document.addEventListener('keydown', keyboard);
    return () => document.removeEventListener('keydown', keyboard);
  }, []);
  useEffect(() => { setPopover(''); setMenu(false); }, [routerLocation.pathname, routerLocation.search]);

  function links(items) {
    return items.map(({ to, icon: Icon, label }) =>
      <NavLink end={to === '/'} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} key={to} to={to}>
        <Icon size={19} strokeWidth={1.9} /><span>{label}</span>
      </NavLink>);
  }
  async function markRead() {
    setError('');
    try { await post('/notificaciones/leer'); notifications.reload(); }
    catch (cause) { setError(errorMessage(cause)); }
  }
  async function leave() {
    try { await logout(); }
    catch (cause) { setError(errorMessage(cause)); }
  }

  return <div className="workspace">
    {menu && <button className="sidebar-overlay" aria-label="Cerrar navegación" onClick={() => setMenu(false)} />}
    <aside className={`sidebar ${menu ? 'open' : ''}`}>
      <Link className="brand-link" to="/"><Brand /></Link>
      <nav aria-label="Menú principal">
        <p className="sidebar-label">OPERACIONES</p>
        {links(allowed.filter((item) => !item.admin))}
        {allowed.some((item) => item.admin) && <><p className="sidebar-label secondary">ADMINISTRACIÓN</p>{links(allowed.filter((item) => item.admin))}</>}
      </nav>
      <div className="sidebar-footer">{links([settings])}
        <div className="workspace-label"><span className="workspace-dot" /> Espacio de trabajo</div>
      </div>
    </aside>
    <div className="workarea">
      <header className="topbar">
        <button className="icon-button menu-trigger" aria-label="Abrir navegación" onClick={() => setMenu(!menu)}><Menu size={24} /></button>
        <button className="top-search" onClick={() => { setSearch(true); setPopover(''); }}><Search size={19} /><span>Buscar pedidos, productos, trabajadores…</span><kbd>Ctrl K</kbd></button>
        <div className="header-controls">
          <button className="icon-button theme-trigger" aria-label={theme === 'light' ? 'Activar modo oscuro' : 'Activar modo claro'} onClick={toggle}>{theme === 'light' ? <Moon size={19} /> : <Sun size={19} />}</button>
          <div className="popover-anchor">
            <button className="icon-button bell-trigger" aria-label={unread ? `Notificaciones, ${unread} sin leer` : 'Notificaciones'} aria-expanded={popover === 'notifications'} onClick={() => { setPopover(popover === 'notifications' ? '' : 'notifications'); setError(''); notifications.reload(); }}>
              <Bell size={21} />{unread > 0 && <i />}
            </button>
            {popover === 'notifications' && <section className="popover notification-popover">
              <header><h2>Notificaciones</h2>{unread > 0 && <Button variant="quiet" icon={CheckCheck} onClick={markRead}>Marcar como leídas</Button>}</header>
              <ErrorNotice message={error || notifications.error} />
              {notifications.loading && !notifications.data ? <Loading /> : notifications.data?.length ? <div className="notification-list">
                {notifications.data.map(({ notification: item, read }) => {
                  const content = <><span className={`notice-dot ${read ? 'read' : ''}`} /><div><strong>{item.title}</strong><p>{item.message}</p><small>{dateLabel(item.createdAt)} · {timeLabel(item.createdAt)}</small></div></>;
                  return item.orderId
                    ? <Link className="notification-item" key={item.id} to={`/pedidos?pedido=${item.orderId}`}>{content}</Link>
                    : <Link className="notification-item" key={item.id} to="/inventario?stock=bajo">{content}</Link>;
                })}
              </div> : <Empty>No tienes notificaciones.</Empty>}
            </section>}
          </div>
          <div className="header-divider" />
          <div className="popover-anchor">
            <button className="profile-trigger" aria-label="Menú de usuario" aria-expanded={popover === 'profile'} onClick={() => { setPopover(popover === 'profile' ? '' : 'profile'); setError(''); }}>
              <span className="avatar">{user.username.split(/[_ .-]/).map((part) => part[0]).slice(0, 2).join('').toUpperCase()}</span>
              <span className="profile-name"><strong>{user.username}</strong><small>{roleName[user.role]}</small></span><ChevronDown size={16} />
            </button>
            {popover === 'profile' && <div className="popover profile-popover">
              <Link to="/configuracion"><CircleHelp size={17} /> Perfil y preferencias</Link>
              <button onClick={leave}><LogOut size={17} /> Cerrar sesión</button>
              <ErrorNotice message={error} />
            </div>}
          </div>
        </div>
      </header>
      <main className="content"><Outlet /></main>
    </div>
    {search && <GlobalSearch user={user} close={() => setSearch(false)} />}
  </div>;
}

function GlobalSearch({ user, close }) {
  const [query, setQuery] = useState('');
  const products = useResource('/productos');
  const orders = useResource('/pedidos');
  const workers = useResource(user.role === 'ADMIN' ? '/trabajadores' : null);
  const value = normalize(query).trim();
  const matches = (label) => normalize(label).includes(value);
  const entries = value.length < 2 ? [] : [
    ...(orders.data || []).filter(({ order, customer }) => matches(orderCode(order.id) + ' ' + customer)).slice(0, 7)
      .map(({ order, customer }) => ({ title: orderCode(order.id), subtitle: customer, Icon: ShoppingCart, to: `/pedidos?pedido=${order.id}` })),
    ...(products.data || []).filter((item) => matches(item.name + ' ' + item.sku)).slice(0, 7)
      .map((item) => ({ title: item.name, subtitle: item.sku, Icon: Package2, to: `/inventario?q=${encodeURIComponent(item.sku)}` })),
    ...(workers.data || []).filter(({ worker }) => matches(fullName(worker))).slice(0, 7)
      .map(({ worker }) => ({ title: fullName(worker), subtitle: worker.position, Icon: Users, to: `/trabajadores?q=${encodeURIComponent(fullName(worker))}` })),
  ];
  return <Dialog title="Buscar en Control360" onClose={close}>
    <div className="input-icon dialog-search"><Search size={19} /><input autoFocus value={query} placeholder="Nombre, código o número de pedido" onChange={(event) => setQuery(event.target.value)} /></div>
    <ErrorNotice message={products.error || orders.error || workers.error} />
    {value.length < 2 ? <p className="muted search-hint">Escribe al menos dos caracteres.</p>
      : products.loading || orders.loading || workers.loading ? <Loading />
        : entries.length ? <div className="search-results">{entries.map(({ title, subtitle, Icon, to }) => <Link key={to} to={to} onClick={close}>
          <Icon size={18} /><div><strong>{title}</strong><small>{subtitle}</small></div><ArrowRight size={16} />
        </Link>)}</div> : <Empty>No encontramos coincidencias.</Empty>}
  </Dialog>;
}
