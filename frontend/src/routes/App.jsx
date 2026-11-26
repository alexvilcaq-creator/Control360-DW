import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { Loading } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import Workspace from '../layouts/Workspace';
import Attendance from '../pages/Attendance';
import Dashboard from '../pages/Dashboard';
import { Categories, Locations, Shifts, Workers } from '../pages/Directory';
import Incidents from '../pages/Incidents';
import PointOfSale, { PaymentHistory } from '../pages/PointOfSale';
import StockAlerts from '../pages/StockAlerts';
import Inventory from '../pages/Inventory';
import Login from '../pages/Login';
import Orders from '../pages/Orders';
import Reports from '../pages/Reports';
import { Settings, UsersPage } from '../pages/Settings';
import { SellerDashboard, WarehouseDashboard } from '../pages/StaffDashboard';
import Warehouse from '../pages/Warehouse';

function Guard({ roles }) {
  const { user, loading } = useAuth();
  if (loading) return <main className="session-loader"><Loading /></main>;
  if (!user) return <Navigate to="/login" replace />;
  return !roles || roles.includes(user.role) ? <Outlet /> : <Navigate to="/" replace />;
}

function LoginPage() {
  const { user, loading } = useAuth();
  if (loading) return <main className="session-loader"><Loading /></main>;
  return user ? <Navigate to="/" replace /> : <Login />;
}

function RoleDashboard() {
  const { user } = useAuth();
  if (user.role === 'VENDEDOR') return <SellerDashboard />;
  if (user.role === 'ALMACEN') return <WarehouseDashboard />;
  return <Dashboard />;
}

export default function App() {
  return <BrowserRouter><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<Guard />}>
      <Route element={<Workspace />}>
        <Route index element={<RoleDashboard />} />
        <Route path="pedidos" element={<Orders />} />
        <Route path="inventario" element={<Inventory />} />
        <Route path="almacen" element={<Warehouse />} />
        <Route path="configuracion" element={<Settings />} />
        <Route path="asistencia" element={<Attendance />} />
        <Route element={<Guard roles={['ADMIN', 'VENDEDOR']} />}>
          <Route path="punto-de-venta" element={<PointOfSale />} />
          <Route path="pagos" element={<PaymentHistory />} />
        </Route>
        <Route element={<Guard roles={['ADMIN', 'ALMACEN']} />}>
          <Route path="alertas-stock" element={<StockAlerts />} />
          <Route path="incidencias" element={<Incidents />} />
        </Route>
        <Route element={<Guard roles={['ADMIN']} />}>
          <Route path="trabajadores" element={<Workers />} />
          <Route path="categorias" element={<Categories />} />
          <Route path="ubicaciones" element={<Locations />} />
          <Route path="turnos" element={<Shifts />} />
          <Route path="reportes" element={<Reports />} />
          <Route path="configuracion/usuarios" element={<UsersPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Route>
  </Routes></BrowserRouter>;
}
