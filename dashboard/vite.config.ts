import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// El bundle final lo sirve nginx desde la raiz del contenedor (ver Dockerfile), asi que no
// hace falta un `base` distinto de '/'. Las URLs de las APIs NO se resuelven aca: se inyectan
// como variables VITE_* en tiempo de build (ver src/config.ts).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
