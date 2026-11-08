/**
 * Regenerates the shared conformance fixtures from the Java engine, plus the site catalog.
 *
 * CI runs this and fails on a git diff, so the Java and TypeScript engines cannot drift without a
 * visible fixture change. The exporter is a standalone single-file Java program; no Maven module
 * or POM is touched.
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const packageRoot = path.resolve(here, '..');
const repoRoot = path.resolve(packageRoot, '..');

const javaExecutable = process.platform === 'win32' ? 'java.exe' : 'java';

function isJavaHome(candidate: string | undefined): candidate is string {
  return candidate !== undefined && existsSync(path.join(candidate, 'bin', javaExecutable));
}

/** Finds a working JDK, preferring a valid JAVA_HOME and then the vendored `.tools` JDKs. */
function findJavaHome(): string | undefined {
  if (isJavaHome(process.env['JAVA_HOME'])) {
    return process.env['JAVA_HOME'];
  }
  const tools = path.join(repoRoot, '.tools');
  if (!existsSync(tools)) {
    return undefined;
  }
  for (const entry of readdirSync(tools)) {
    if (!entry.toLowerCase().includes('jdk')) {
      continue;
    }
    const direct = path.join(tools, entry);
    if (isJavaHome(direct)) {
      return direct;
    }
    for (const child of readdirSync(direct)) {
      const nested = path.join(direct, child);
      if (isJavaHome(nested)) {
        return nested;
      }
    }
  }
  return undefined;
}

const javaHome = findJavaHome();
const java = javaHome === undefined ? 'java' : path.join(javaHome, 'bin', javaExecutable);
const commandEnv: NodeJS.ProcessEnv =
  javaHome === undefined ? process.env : { ...process.env, JAVA_HOME: javaHome };

function run(command: string, args: readonly string[], label: string): void {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    stdio: 'inherit',
    env: commandEnv,
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`${label} failed with exit code ${String(result.status)}`);
  }
}

function runMaven(args: readonly string[]): void {
  run('bash', ['./mvnw', ...args], 'mvnw');
}

const coreClasses = path.join(repoRoot, 'inflector-core', 'target', 'classes');
const exampleClasses = path.join(repoRoot, 'inflector-example', 'target', 'classes');
const exporter = path.join(here, 'export-fixtures', 'ExportFixtures.java');
const gold = path.join(repoRoot, 'results', 'release-1.2.0', 'eval', 'p1-test-gold.tsv');
const fixtures = path.join(repoRoot, 'test-fixtures');

runMaven(['-B', '-q', '-pl', 'inflector-core', '-am', '-DskipTests', 'compile']);
run(java, ['-cp', coreClasses, exporter, gold, fixtures], 'fixture export');

runMaven(['-B', '-q', '-pl', 'inflector-example', '-am', '-DskipTests', 'compile']);
run(
  java,
  [
    '-cp',
    `${coreClasses}${path.delimiter}${exampleClasses}`,
    'io.github.slayyyyer.morphex.inflector.example.SiteCatalog',
    path.join(repoRoot, 'site', 'catalog.json'),
  ],
  'site catalog',
);
