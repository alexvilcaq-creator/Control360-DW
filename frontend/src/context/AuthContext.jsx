import { createContext, useContext, useEffect, useState } from 'react';
import { get, post, resetCsrf } from '../services/api';

const AuthContext = createContext(null);
export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let mounted = true;
    get('/auth/me').then((profile) => { if (mounted) setUser(profile); })
      .catch(() => {}).finally(() => { if (mounted) setLoading(false); });
    const expire = () => setUser(null);
    window.addEventListener('control360:session-ended', expire);
    return () => {
      mounted = false;
      window.removeEventListener('control360:session-ended', expire);
    };
  }, []);
  async function login(username, password) {
    const profile = await post('/auth/login', { username, password });
    resetCsrf();
    setUser(profile);
  }
  async function logout() {
    await post('/auth/logout');
    resetCsrf();
    setUser(null);
  }
  return <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>;
}
