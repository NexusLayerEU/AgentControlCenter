import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The production build lands straight in the daemon's static resources so a
// single `java -jar` serves both the API and the dashboard on :4000.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:4000',
      '/hooks': 'http://127.0.0.1:4000',
      '/ws': { target: 'ws://127.0.0.1:4000', ws: true },
    },
  },
})
