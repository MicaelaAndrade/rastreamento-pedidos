/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Em desenvolvimento, o Vite repassa as chamadas /api para o back-end,
// então o front e a API parecem estar na mesma origem (sem problema de CORS).
const apiTarget = process.env.API_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        // Quem chama a API é o próprio proxy, não o navegador. Sem remover o Origin,
        // a API trataria a chamada como CORS e recusaria (403) quando o front é aberto
        // por outro endereço, como a URL pública do GitHub Codespaces.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
