import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // lightningcss (minificador padrão) não carregou seu binário nativo no Windows; o CSS é pequeno.
  build: { cssMinify: false },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
