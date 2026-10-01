import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync, renameSync, rmSync, realpathSync, statfsSync, openSync, closeSync } from 'node:fs';
import { resolve, join, dirname, relative, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const self = fileURLToPath(import.meta.url);
const template = resolve(dirname(self), '../assets/basic-app-template');
const [command, ...args] = process.argv.slice(2);
const fail = message => { throw new Error(message); };
function atomic(path, value) {
  const tmp = `${path}.${process.pid}.tmp`;
  writeFileSync(tmp, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
  renameSync(tmp, path);
}
function rootFor(slug) {
  if (!/^[a-z][a-z0-9-]{1,63}$/.test(slug ?? '')) fail('Use a stable project slug: lowercase letters, digits, hyphens.');
  const base = resolve('projects');
  mkdirSync(base, { recursive: true });
  if (realpathSync(base) !== base) fail('Projects directory must not be a symlink.');
  const root = join(base, slug);
  if (existsSync(root) && realpathSync(root) !== root) fail('Project directory must not be a symlink.');
  return root;
}
function read(root) { return JSON.parse(readFileSync(join(root, 'project.json'), 'utf8')); }
function status(root) {
  const meta = read(root);
  if (meta.state === 'building') {
    let alive = false;
    try { const pid = Number(readFileSync(join(root, 'build.lock'), 'utf8')); if (pid > 0) { process.kill(pid, 0); alive = true; } } catch {}
    if (!alive) return save(root, { state: 'build_interrupted', error: 'Build worker is no longer running. Inspect the saved log before resuming.' });
  }
  return meta;
}
function save(root, patch) {
  const value = { ...read(root), ...patch, updatedAt: new Date().toISOString() };
  atomic(join(root, 'project.json'), value);
  return value;
}
function exec(bin, params) {
  const r = spawnSync(bin, params, { encoding: 'utf8', timeout: 15000 });
  return { ok: !r.error && r.status === 0, text: (r.stdout ?? '').trim() };
}
function check(root) {
  const api = Number(exec('/system/bin/getprop', ['ro.build.version.sdk']).text) || null;
  const abi = exec('/system/bin/getprop', ['ro.product.cpu.abilist']).text || process.arch;
  const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || join(process.env.HOME, 'android-sdk');
  const checks = {
    java: exec('java', ['-version']).ok,
    gradle: exec('gradle', ['--version']).ok,
    aapt2: exec('aapt2', ['version']).ok,
    platform36: existsSync(join(sdk, 'platforms/android-36/android.jar')),
    buildTools36: existsSync(join(sdk, 'build-tools/36.0.0/source.properties')),
  };
  const fs = statfsSync(root);
  const result = { api, abi, sdk, freeBytes: fs.bavail * fs.bsize, checks,
    deviceCompatible: api !== null && api >= 30,
    ready: api !== null && api >= 30 && Object.values(checks).every(Boolean),
    missing: Object.entries(checks).filter(([,ok]) => !ok).map(([name]) => name) };
  atomic(join(root, 'environment.json'), result);
  return result;
}
function initialize(slug, packageName, label) {
  if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}$/.test(packageName ?? '')) fail('Supply an explicit unique Android package ID.');
  if (packageName.startsWith('one.behavio.mobilebotui')) fail('Do not replace Mobile Bot.');
  if (!label?.trim() || label.length > 80) fail('Supply an app name of 1–80 characters.');
  const root = rootFor(slug);
  if (existsSync(root)) {
    const old = read(root);
    if (old.packageName !== packageName || old.name !== label) fail('Existing project identity differs. Reuse its identity; do not overwrite it.');
    return { ...old, root, reused: true };
  }
  const stage = `${root}.creating-${process.pid}`;
  try {
    mkdirSync(stage);
    cpSync(template, join(stage, 'app-project'), { recursive: true });
    const gradle = join(stage, 'app-project/app/build.gradle.kts');
    writeFileSync(gradle, readFileSync(gradle, 'utf8').replaceAll('one.behavio.generated.clock', packageName));
    const javaRoot = join(stage, 'app-project/app/src/main/java');
    const oldJava = join(javaRoot, 'one/behavio/generated/clock/MainActivity.java');
    const newJava = join(javaRoot, packageName.replaceAll('.', '/'), 'MainActivity.java');
    const java = readFileSync(oldJava, 'utf8').replaceAll('one.behavio.generated.clock', packageName);
    rmSync(oldJava); mkdirSync(dirname(newJava), { recursive: true }); writeFileSync(newJava, java);
    const xml = label.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    const manifest = join(stage, 'app-project/app/src/main/AndroidManifest.xml');
    writeFileSync(manifest, readFileSync(manifest, 'utf8').replace('android:label="Zegar"', `android:label="${xml}"`));
    mkdirSync(join(stage, 'reports')); mkdirSync(join(stage, 'artifacts'));
    atomic(join(stage, 'project.json'), { schemaVersion: 1, slug, name: label, packageName,
      minSdk: 30, compileSdk: 36, targetSdk: 36, source: 'app-project', state: 'created',
      createdAt: new Date().toISOString(), lastBuild: null });
    writeFileSync(join(stage, 'README.md'), `# ${label}\n\nPackage: ${packageName}\nAndroid 11+ (API 30).\n\nSource: app-project/\nBuild and test evidence: reports/\nVerified APK: artifacts/\nProgress: project.json\n\nDescribe accepted requirements, data storage, tests and current limitations here before implementation.\n`);
    writeFileSync(join(stage, '.gitignore'), 'artifacts/\n**/build/\n**/.gradle/\n**/local.properties\n*.keystore\n*.jks\n');
    renameSync(stage, root);
    return { ...read(root), root, reused: false };
  } catch (error) { rmSync(stage, { recursive: true, force: true }); throw error; }
}
function sourceDir(root, meta) {
  const source = realpathSync(resolve(root, meta.source));
  const rel = relative(root, source);
  if (rel.startsWith('..') || isAbsolute(rel)) fail('Source must remain inside this project.');
  return source;
}
function build(root) {
  const meta = read(root);
  const source = sourceDir(root, meta);
  const lock = join(root, 'build.lock');
  if (existsSync(lock)) {
    const pid = Number(readFileSync(lock, 'utf8'));
    let alive = false; try { process.kill(pid, 0); alive = true; } catch {}
    if (alive) return { state: 'building', pid, reused: true };
    rmSync(lock);
  }
  const environment = check(root);
  if (!environment.ready) { save(root, { state: 'environment_required' }); return environment; }
  const fd = openSync(lock, 'wx', 0o600);
  writeFileSync(fd, String(process.pid)); closeSync(fd);
  const id = new Date().toISOString().replaceAll(':', '-');
  const log = join(root, 'reports', `build-${id}.log`);
  save(root, { state: 'building', error: null, lastBuild: { id, log: relative(root, log), startedAt: new Date().toISOString() } });
  const child = spawn(process.execPath, [self, '_worker', root, source, environment.sdk, log], {
    detached: true, stdio: 'ignore', env: process.env,
  });
  child.on('error', error => { save(root, { state: 'build_failed', error: error.message }); rmSync(lock, { force: true }); });
  if (child.pid) writeFileSync(lock, String(child.pid));
  child.unref();
  return { state: 'building', pid: child.pid, log };
}
async function worker(root, source, sdk, log) {
  const lock = join(root, 'build.lock');
  try {
    const aapt = exec('sh', ['-c', 'command -v aapt2']);
    if (!aapt.ok) fail('Native aapt2 unavailable.');
    const fd = openSync(log, 'a', 0o600);
    const result = await new Promise((resolveResult, reject) => {
      const child = spawn('gradle', ['--no-daemon', '--max-workers=2', '-Dorg.gradle.vfs.watch=false',
        `-Pandroid.aapt2FromMavenOverride=${aapt.text}`, 'assembleDebug', 'lintDebug'], {
        cwd: source, env: { ...process.env, ANDROID_HOME: sdk, ANDROID_SDK_ROOT: sdk }, stdio: ['ignore', fd, fd],
      });
      child.on('error', reject); child.on('close', code => resolveResult(code));
    }).finally(() => closeSync(fd));
    if (result !== 0) fail(`Build/lint exited with code ${result}; inspect the saved log.`);
    const apk = join(source, 'app/build/outputs/apk/debug/app-debug.apk');
    const badging = exec(aapt.text, ['dump', 'badging', apk]);
    const expected = read(root).packageName;
    if (!badging.ok || !badging.text.includes(`package: name='${expected}'`) || !badging.text.includes('launchable-activity:')) fail('APK identity or launcher verification failed.');
    if (!/(?:minSdkVersion|sdkVersion):'30'/.test(badging.text)) fail('The APK must retain Android 11/API30 support.');
    const signature = exec('java', ['-jar', join(sdk, 'build-tools/36.0.0/lib/apksigner.jar'), 'verify', '--print-certs', apk]);
    const fingerprint = signature.text.match(/certificate SHA-256 digest: ([a-f0-9]+)/i)?.[1]?.toLowerCase();
    if (!signature.ok || !fingerprint) fail('APK signature verification failed.');
    const previous = read(root).signingSha256;
    if (previous && previous !== fingerprint) fail('Signing identity changed. Restore the original key; do not uninstall the user app.');
    const target = join(root, 'artifacts/app-debug.apk'); cpSync(apk, target);
    const hash = createHash('sha256').update(readFileSync(target)).digest('hex');
    writeFileSync(join(root, 'reports/apk-manifest.txt'), badging.text);
    save(root, { state: 'apk_ready', signingSha256: fingerprint, error: null, lastBuild: { ...read(root).lastBuild, finishedAt: new Date().toISOString(), apk: 'artifacts/app-debug.apk', sha256: hash } });
  } catch (error) { save(root, { state: 'build_failed', error: error.message }); }
  finally { rmSync(lock, { force: true }); }
}
try {
  if (command === '_worker') await worker(...args);
  else if (command === 'init') console.log(JSON.stringify(initialize(...args)));
  else if (command === 'check') console.log(JSON.stringify(check(rootFor(args[0]))));
  else if (command === 'build') console.log(JSON.stringify(build(rootFor(args[0]))));
  else if (command === 'status') console.log(JSON.stringify(status(rootFor(args[0]))));
  else fail('Commands: init SLUG PACKAGE NAME | check SLUG | build SLUG | status SLUG. Run from the agent workspace.');
} catch (error) { console.error(JSON.stringify({ ok: false, error: error.message })); process.exitCode = 1; }
