import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const source = readFileSync(new URL('../../app/src/main/assets/bootstrap.sh', import.meta.url), 'utf8');
const block = source.slice(source.indexOf('export DEBIAN_FRONTEND'), source.indexOf('# Keep the one-time code')).replaceAll('__CODEX_VERSION__', '0.153.4');
const stopRuntimeBlock = source.slice(source.indexOf('stop_runtime_pid()'), source.indexOf('stop_local_ai_processes()'));
function run({ codex = '0.153.4', nodeMissing = false, packageFailure = false, timeoutExit = null } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'mobile-bootstrap-reuse-'));
  for (const path of ['bin', 'etc/tls', 'etc/apt', 'install']) mkdirSync(join(root, path), { recursive: true });
  writeFileSync(join(root, 'etc/tls/cert.pem'), 'fixture');
  writeFileSync(join(root, 'bin/codex'), 'existing-launcher');
  const script = `set -eu
export PREFIX="$QA_ROOT" INSTALL_ROOT="$QA_ROOT/install" RESOLV_FILE="$QA_ROOT/install/resolv.conf"
write_status() { echo "phase:$1"; }
node() { ${nodeMissing ? 'return 1' : 'echo v24.18.0'}; }
proot() { echo proot; }
timeout() { ${timeoutExit ? `return ${timeoutExit}` : codex ? `echo 'codex-cli ${codex}'` : 'return 1'}; }
pkg() { echo "pkg:$*"; ${packageFailure ? 'return 1' : 'return 0'}; }
npm() { echo "npm:$*"; }
${block}`;
  const result = spawnSync('bash', ['-c', script], { env: { ...process.env, QA_ROOT: root }, encoding: 'utf8' });
  const launcher = readFileSync(join(root, 'bin/codex'), 'utf8');
  rmSync(root, { recursive: true, force: true });
  return { ...result, launcher };
}

test('working runtime and Codex perform no downloads or launcher replacement', () => {
  const r = run();
  assert.equal(r.status, 0, r.stderr);
  assert.doesNotMatch(r.stdout, /(?:npm|pkg):/);
  assert.match(r.stdout, /bootstrap_runtime_reused/);
  assert.match(r.stdout, /bootstrap_codex_reused/);
  assert.equal(r.launcher, 'existing-launcher');
});

test('missing Codex installs Codex without reinstalling working runtime packages', () => {
  const r = run({ codex: null });
  assert.equal(r.status, 0, r.stderr);
  assert.doesNotMatch(r.stdout, /pkg:/);
  assert.equal((r.stdout.match(/npm:install/g) ?? []).length, 2);
  assert.match(r.launcher, /proot/);
});

test('only missing runtime dependency is requested', () => {
  const r = run({ nodeMissing: true });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.stdout, /pkg:install --reinstall -y nodejs-lts/);
  assert.doesNotMatch(r.stdout, /pkg:install.*proot|npm:/);
});

test('package failure is bounded to one retry and never starts Codex installation', () => {
  const r = run({ nodeMissing: true, packageFailure: true });
  assert.notEqual(r.status, 0);
  assert.equal((r.stdout.match(/pkg:update/g) ?? []).length, 2);
  assert.doesNotMatch(r.stdout, /npm:/);
});

test('an already working different Codex version is preserved', () => {
  const r = run({ codex: '0.154.0' });
  assert.equal(r.status, 0, r.stderr);
  assert.doesNotMatch(r.stdout, /(?:npm|pkg):/);
  assert.equal(r.launcher, 'existing-launcher');
});

test('a slow version check never triggers reinstallation', () => {
  const r = run({ timeoutExit: 124 });
  assert.notEqual(r.status, 0);
  assert.doesNotMatch(r.stdout, /(?:npm|pkg):/);
  assert.match(r.stdout, /refusing_reinstall/);
  assert.equal(r.launcher, 'existing-launcher');
});

test('stopping an already exited runtime stays successful under set -e', () => {
  const result = spawnSync('bash', ['-c', `set -eu\n${stopRuntimeBlock}\nstop_runtime_pid 999999\necho stopped`], {
    encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /stopped/);
  assert.match(stopRuntimeBlock, /kill -TERM/);
  assert.match(stopRuntimeBlock, /kill -KILL/);
});

test('bootstrap stops llama before repairing its mapped native libraries', () => {
  const localBlockStart = source.indexOf('if [ "$RUNTIME_SELECTION" = "pi-local" ]');
  const hostStopCall = source.indexOf('  stop_existing_host\n', localBlockStart);
  const stopCall = source.indexOf('  stop_local_ai_processes\n', localBlockStart);
  const nativeRepair = source.indexOf('find "$LOCAL_AI_ROOT/lib"', localBlockStart);
  assert.ok(hostStopCall > localBlockStart, 'missing early Host stop');
  assert.ok(stopCall > hostStopCall, 'local engine scan must follow Host shutdown');
  assert.ok(stopCall > localBlockStart, 'missing early local engine stop');
  assert.ok(nativeRepair > stopCall, 'native library repair must happen after the engine stops');
  assert.match(source, /kill -KILL "\$target_pid"[\s\S]*kill -0 "\$target_pid"/);
  assert.match(source, /readlink -f "\$process_dir\/exe"/);
  assert.match(source, /pgrep -f 'llama-server\.\*--port 8769'/);
  assert.match(source, /bootstrap_llama_stop pid=%s source=pgrep/);
  assert.match(source, /bootstrap_host_stop pid=%s source=pid_file/);
});
