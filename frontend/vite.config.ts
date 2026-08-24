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
    // Restores every `vi.stubGlobal` / `vi.spyOn` *before* each test, so no test file needs its
    // own teardown and a failed assertion cannot leak a spy into the next test. Mind the timing:
    // a spy is still live during `afterEach`, and one installed in `beforeAll` is wiped before the
    // first test rather than kept.
    unstubGlobals: true,
    restoreMocks: true,
  },
})
