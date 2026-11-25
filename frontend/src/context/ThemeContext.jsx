import { createContext, useContext, useEffect, useState } from 'react';
const ThemeContext = createContext(null);
export const useTheme = () => useContext(ThemeContext);

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => localStorage.getItem('control360-theme') || 'light');
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('control360-theme', theme);
  }, [theme]);
  return <ThemeContext.Provider value={{ theme, toggle: () => setTheme((value) => value === 'light' ? 'dark' : 'light'), setTheme }}>
    {children}
  </ThemeContext.Provider>;
}
