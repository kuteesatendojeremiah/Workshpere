// Starts a THROWAWAY PostgreSQL container (port 5433, never your dev database on 5432) and the
// Spring Boot backend pointed at it. Playwright's webServer runs this and kills it afterwards;
// on exit we also remove the container so every run starts from an empty schema.
import { spawn, spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const CONTAINER = 'worksphere-e2e-db'
const backendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../backend')

function docker(...args) {
  return spawnSync('docker', args, { encoding: 'utf8' })
}

function removeContainer() {
  docker('rm', '-f', CONTAINER)
}

removeContainer()
const started = docker(
  'run', '-d', '--rm', '--name', CONTAINER, '-p', '5433:5432',
  '-e', 'POSTGRES_DB=worksphere_e2e', '-e', 'POSTGRES_USER=e2e', '-e', 'POSTGRES_PASSWORD=e2e',
  'postgres:16',
)
if (started.status !== 0) {
  console.error('Could not start the E2E database container. Is Docker running?\n' + started.stderr)
  process.exit(1)
}

// pg_isready can report "ready" during the image's temporary init server; require two successes in a row.
let ok = 0
for (let i = 0; i < 60 && ok < 2; i++) {
  ok = docker('exec', CONTAINER, 'pg_isready', '-U', 'e2e', '-d', 'worksphere_e2e').status === 0 ? ok + 1 : 0
  if (ok < 2) await new Promise((r) => setTimeout(r, 1000))
}
if (ok < 2) {
  removeContainer()
  console.error('E2E database never became ready')
  process.exit(1)
}

const isWindows = process.platform === 'win32'

// mvnw.cmd insists on JAVA_HOME. If it is not set, ask the `java` on the PATH where it lives.
function findJavaHome() {
  if (process.env.JAVA_HOME) {
    return process.env.JAVA_HOME
  }
  const probe = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8' })
  const match = /java\.home = (.+)/.exec(`${probe.stdout}${probe.stderr}`)
  return match ? match[1].trim() : undefined
}
// Absolute path + one command string: on Windows a .cmd file is only found via the shell, and
// cmd.exe does not look in the working directory for a bare "mvnw.cmd".
const mvnw = path.join(backendDir, isWindows ? 'mvnw.cmd' : 'mvnw')
const backend = spawn(`"${mvnw}" -q spring-boot:run`, {
  cwd: backendDir,
  shell: true,
  stdio: 'inherit',
  env: {
    ...process.env,
    JAVA_HOME: findJavaHome(),
    DB_URL: 'jdbc:postgresql://localhost:5433/worksphere_e2e',
    DB_USER: 'e2e',
    DB_PASSWORD: 'e2e',
  },
})

function shutdown() {
  if (isWindows) {
    spawnSync('taskkill', ['/pid', String(backend.pid), '/T', '/F'])
  } else {
    backend.kill('SIGTERM')
  }
  removeContainer()
}
process.on('SIGINT', () => {
  shutdown()
  process.exit(0)
})
process.on('SIGTERM', () => {
  shutdown()
  process.exit(0)
})
process.on('exit', removeContainer)
backend.on('exit', (code) => {
  removeContainer()
  process.exit(code ?? 1)
})
