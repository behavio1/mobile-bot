// Pinned Google SDK archives, checked against repository2-1.xml on 2026-09-09.
import { existsSync, mkdirSync, readFileSync, writeFileSync, renameSync, readdirSync, rmSync, statfsSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || join(process.env.HOME, 'android-sdk');
const stage = join(sdk, '.mobile-bot-downloads');
const packages = [
  { id: 'platforms/android-36', file: 'platform-36_r02.zip', sha1: '2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576', marker: 'android.jar' },
  { id: 'build-tools/36.0.0', file: 'build-tools_r36_linux.zip', sha1: 'b0b6376977657e8ad9b969bacf4093601da2c6fb', marker: 'lib/d8.jar' },
];
function log(event, details = {}) { console.log(JSON.stringify({ time: new Date().toISOString(), event, ...details })); }
function run(bin, args, capture = false) {
  const r = spawnSync(bin, args, { encoding: 'utf8', stdio: capture ? 'pipe' : 'inherit', timeout: 8 * 60_000 });
  if (r.error || r.status !== 0) throw new Error(`${bin} failed (${r.status ?? r.error?.code})`);
  return r.stdout?.trim();
}
function checksum(file) { return createHash('sha1').update(readFileSync(file)).digest('hex'); }
function checkpoint(value) {
  const tmp = join(stage, 'status.tmp'); writeFileSync(tmp, JSON.stringify(value, null, 2)); renameSync(tmp, join(stage, 'status.json'));
}
try {
  if (!existsSync('/system/bin/getprop') || !process.env.PREFIX?.includes('com.termux')) throw new Error('Run this installer inside Termux on the target Android phone.');
  const api = Number(run('/system/bin/getprop', ['ro.build.version.sdk'], true));
  if (api < 30) throw new Error('Android 11/API30 or newer is required.');
  mkdirSync(stage, { recursive: true });
  log('toolchain_setup_start', { sdk, api, arch: process.arch });
  const space = statfsSync(sdk);
  if (space.bavail * space.bsize < 2 * 1024 ** 3) throw new Error('At least 2 GiB free space is required before preparing build tools; larger projects need more.');
  checkpoint({ state: 'packages', completed: [] });
  run('pkg', ['install', '-y', '-o', 'Dpkg::Options::=--force-confold', '-o', 'Dpkg::Options::=--force-confdef', 'openjdk-21', 'gradle', 'aapt2', 'unzip', 'curl', 'openssl']);
  const completed = [];
  for (const item of packages) {
    const target = join(sdk, item.id);
    if (existsSync(join(target, item.marker)) && existsSync(join(target, 'source.properties'))) {
      completed.push(item.id); log('sdk_package_reused', { id: item.id }); continue;
    }
    if (existsSync(target)) throw new Error(`Incomplete SDK directory ${item.id}; inspect it before replacing user files.`);
    checkpoint({ state: 'download', current: item.id, completed });
    const zip = join(stage, item.file);
    if (!existsSync(zip) || checksum(zip) !== item.sha1) {
      log('sdk_download_start', { id: item.id, url: `https://dl.google.com/android/repository/${item.file}`, retries: 3 });
      run('curl', ['--fail', '--location', '--retry', '3', '--retry-delay', '2', '--connect-timeout', '30', '--max-time', '420', '-o', zip + '.part', `https://dl.google.com/android/repository/${item.file}`]);
      if (checksum(zip + '.part') !== item.sha1) throw new Error(`SDK checksum mismatch: ${item.id}`);
      renameSync(zip + '.part', zip);
    }
    const entries = run('unzip', ['-Z1', zip], true).split('\n');
    if (entries.some(p => p.startsWith('/') || p.includes('\\') || p.split('/').includes('..'))) throw new Error('Unsafe SDK archive path.');
    const unpack = join(stage, `unpack-${process.pid}`); mkdirSync(unpack, { recursive: true });
    try {
      run('unzip', ['-q', '-o', zip, '-d', unpack]);
      const candidates = readdirSync(unpack).map(p => join(unpack, p));
      const payload = candidates.find(p => existsSync(join(p, 'source.properties')) && existsSync(join(p, item.marker)));
      if (!payload) throw new Error(`Unexpected SDK archive layout: ${item.id}`);
      mkdirSync(join(target, '..'), { recursive: true }); renameSync(payload, target);
    } finally { rmSync(unpack, { recursive: true, force: true }); }
    completed.push(item.id); checkpoint({ state: 'installed', completed }); log('sdk_package_installed', { id: item.id });
  }
  checkpoint({ state: 'ready', completed }); log('toolchain_setup_complete', { sdk });
} catch (error) { log('toolchain_setup_failed', { error: error.message }); process.exitCode = 1; }
