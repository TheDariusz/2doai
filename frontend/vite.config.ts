/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Mirror Pattern B same-origin locally: forward /api to the Spring backend.
  // In production Cloudflare's Pages Function does this instead (no CORS either way).
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    // Undoes every `vi.stubGlobal` after each test, so no test file needs its own teardown.
    unstubGlobals: true,
  },
})
