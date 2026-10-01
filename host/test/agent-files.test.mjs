import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtempSync, mkdirSync, writeFileSync, symlinkSync, rmSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHostServer, MemoryWorkspaceStore } from '../dist/index.js';

test('agent documents: real nested Markdown, read-only, no cross-agent or symlink reads', async t => {
  const root = mkdtempSync(join(tmpdir(), 'agent-documents-'));
  const workspace = join(root, 'starter-atlas');
  mkdirSync(join(workspace, 'wyniki'), { recursive: true });
  mkdirSync(join(root, 'starter-nova'));
  const content = '# Raport\n\n**Pełna treść** — ąęółż\n';
  writeFileSync(join(workspace, 'wyniki', 'raport test.MD'), content);
  writeFileSync(join(workspace, 'wyniki', 'notes.markdown'), '# Notes');
  writeFileSync(join(workspace, 'wyniki', 'private.json'), '{}');
  writeFileSync(join(root, 'starter-nova', 'other.md'), 'other agent');
  symlinkSync(join(root, 'starter-nova', 'other.md'), join(workspace, 'link.md'));
  symlinkSync(join(root, 'starter-nova'), join(workspace, 'other'));
  const server = createHostServer(() => ({ authenticated: true }), undefined, new MemoryWorkspaceStore(), {
    agentWorkspaceRoot: root, backfillConversationStarters: false,
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.close(); rmSync(root, { recursive: true, force: true }); });
  const base = `http://127.0.0.1:${server.address().port}/agents/starter-atlas`;
  const get = (kind, path = '') => fetch(`${base}/${kind}?path=${encodeURIComponent(path)}`);
  assert.deepEqual((await (await get('files')).json()).entries.map(x => x.name), ['wyniki']);
  const files = (await (await get('files', 'wyniki')).json()).entries;
  assert.deepEqual(files.map(x => x.name).sort(), ['notes.markdown', 'raport test.MD']);
  assert.equal((await (await get('file', 'wyniki/raport test.MD')).json()).content, content);
  for (const path of ['../starter-nova/other.md', '/etc/other.md', 'link.md', 'other/other.md', 'wyniki/private.json', 'wyniki/..\\other.md']) {
    assert.equal((await get('file', path)).status, 400, path);
  }
  assert.equal((await get('file', 'missing.md')).status, 404);
  assert.equal((await get('files', 'wyniki/raport test.MD')).status, 404);
  assert.equal((await fetch(`${base}/file?path=wyniki/raport%20test.MD`, { method: 'DELETE' })).status, 405);
  assert.equal((await fetch(`${base}/file?path=wyniki/raport%20test.MD`, { method: 'PUT', body: 'changed' })).status, 405);
  assert.equal(readFileSync(join(workspace, 'wyniki', 'raport test.MD'), 'utf8'), content);
  writeFileSync(join(workspace, 'large.md'), 'x'.repeat(2 * 1024 * 1024 + 1));
  assert.equal((await get('file', 'large.md')).status, 413);
  writeFileSync(join(workspace, 'binary.md'), Buffer.from([0, 255]));
  assert.equal((await get('file', 'binary.md')).status, 422);
  const unknown = await fetch(`http://127.0.0.1:${server.address().port}/agents/unknown/files`);
  assert.equal(unknown.status, 404);
  const noFolder = await fetch(`http://127.0.0.1:${server.address().port}/agents/starter-echo/files`);
  assert.deepEqual((await noFolder.json()).entries, []);
});
