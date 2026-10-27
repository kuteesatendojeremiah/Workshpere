/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    // e2e/ holds Playwright specs, which Vitest must not try to run.
    exclude: ['e2e/**', 'node_modules/**'],
    // The default "forks" pool hangs in some sandboxed environments (process spawning
    // restrictions); "threads" works everywhere it's been tried for this project.
    pool: 'threads',
    // Spawning 8 jsdom workers at once overloads some machines (start-up timeouts), so cap it.
    maxWorkers: 2,
    testTimeout: 30000,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
      reporter: ['text-summary', 'html'],
    },
  },
})
