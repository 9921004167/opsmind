import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // Only used for `npm run dev` outside Docker. Inside Docker the built
    // static files are served by nginx, which does the /api proxying instead
    // (see nginx.conf) - this proxy exists so `npm run dev` also works
    // directly against a locally-running opsmind-core without CORS issues.
    proxy: {
      '/api': 'http://localhost:8080',
      '/health-proxy/order': { target: 'http://localhost:8093', rewrite: (p) => p.replace(/^\/health-proxy\/order/, '/actuator/health') },
      '/health-proxy/payment': { target: 'http://localhost:8091', rewrite: (p) => p.replace(/^\/health-proxy\/payment/, '/actuator/health') },
      '/health-proxy/inventory': { target: 'http://localhost:8092', rewrite: (p) => p.replace(/^\/health-proxy\/inventory/, '/actuator/health') },
      '/health-proxy/catalog': { target: 'http://localhost:8090', rewrite: (p) => p.replace(/^\/health-proxy\/catalog/, '/actuator/health') }
    }
  }
});
