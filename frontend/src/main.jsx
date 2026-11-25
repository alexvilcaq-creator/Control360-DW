import '@fontsource-variable/inter';
import './assets/styles.css';
import { createRoot } from 'react-dom/client';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import App from './routes/App';

createRoot(document.getElementById('root')).render(
  <ThemeProvider><AuthProvider><App /></AuthProvider></ThemeProvider>,
);
