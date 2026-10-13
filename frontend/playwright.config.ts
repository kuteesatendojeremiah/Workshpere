import { defineConfig } from '@playwright/test'

// Runs the REAL app: a throwaway Postgres container + the Spring Boot backend (e2e/start-backend.mjs)
// and the Vite dev server. reuseExistingServer is false on purpose, so a run can never silently
// talk to - and write test users into - your everyday dev backend or database.
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: [
    {
      command: 'node e2e/start-backend.mjs',
      url: 'http://localhost:8080/health',
      timeout: 300_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: false,
    },
  ],
})
