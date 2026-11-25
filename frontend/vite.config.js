import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwind from '@tailwindcss/vite';

const allowedHosts = (process.env.VITE_ALLOWED_HOSTS || '').split(',').filter(Boolean);
const proxy = { '/api': process.env.API_TARGET || 'http://127.0.0.1:8080' };

export default defineConfig({
  plugins: [react(), tailwind()],
  server: { proxy, allowedHosts },
  preview: { proxy, allowedHosts },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/node_modules/recharts/') || id.includes('/node_modules/d3-')) return 'charts';
        },
      },
    },
  },
});
