import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { SqliteWorkspaceStore, MemoryWorkspaceStore, createHostServer } from '../dist/index.js';

for (const backend of ['sqlite', 'memory']) test(`${backend}: exact conversation totals, scoped deletion, no starter resurrection`, () => {
  const dir = mkdtempSync(join(tmpdir(), 'agent-cards-'));
  try {
    const dbPath = join(dir, 'workspace.sqlite');
    const store = backend === 'sqlite' ? new SqliteWorkspaceStore(dbPath) : new MemoryWorkspaceStore();
    for (let n=0; n<35; n++) {
      const run = store.prepareRun('starter-atlas', `conversation ${n}`);
      store.completeRun(run.runId, run.conversation.id, `thread-${n}`, 'done');
    }
    const nova = store.prepareRun('starter-nova', 'keep');
    store.completeRun(nova.runId, nova.conversation.id, 'nova-thread', 'keep');
    const atlas = store.prepareRun('starter-atlas', 'recurring');
    const automation = store.createAutomation({agentId:'starter-atlas', conversationId:atlas.conversation.id,
      name:'monitor',prompt:'monitor', intervalMinutes:15},new Date()).automation;
    store.markAutomationRunning(automation.id, new Date());
    store.finishAutomation(automation.id, 'completed', 'done', new Date());
    const before = store.workspace();
    assert.equal(before.agents.find(a=>a.id==='starter-atlas').conversationCount,36);
    assert.equal(before.agents.find(a=>a.id==='starter-nova').conversationCount,1);
    assert.equal(before.recentConversations.length,30);
    store.deleteAgent('starter-atlas');
    assert.throws(()=>store.agent('starter-atlas'));
    const after = store.workspace();
    assert.equal(after.automations.some(a=>a.agentId==='starter-atlas'),false);
    assert.equal(after.recentCompletedTasks.some(a=>a.agentId==='starter-atlas'),false);
    assert.equal(after.agents.find(a=>a.id==='starter-nova').conversationCount,1);
    assert.equal(store.messages(nova.conversation.id).length,2);
    if (backend==='sqlite') {
      const db = new DatabaseSync(dbPath);
      for (const table of ['conversations','runs','messages','automations','automation_occurrences']) {
        const expected = table==='messages'?2: ['conversations','runs'].includes(table)?1:0;
        assert.equal(db.prepare(`SELECT COUNT(*) AS n FROM ${table}`).get().n,expected,table);
      }
      assert.deepEqual(db.prepare('PRAGMA foreign_key_check').all(),[]);
      const plan = db.prepare('EXPLAIN QUERY PLAN SELECT agent_id,COUNT(*) FROM conversations GROUP BY agent_id').all();
      assert.equal(plan.filter(r=>r.detail.includes('SCAN conversations')).length,1);
      db.close();
      const reopened = new SqliteWorkspaceStore(dbPath);
      assert.equal(reopened.workspace().agents.some(a=>a.id==='starter-atlas'),false);
    }
  } finally { rmSync(dir,{recursive:true,force:true}); }
});

test('HTTP deletion refuses a running agent, then removes only its data', async () => {
  const store = new MemoryWorkspaceStore();
  let finishRun;
  const server = createHostServer(
    ()=>({installed:true,version:'codex-cli 0.153.4',authenticated:true}),
    ()=>new Promise(resolve=>{finishRun=resolve;}),store,
  );
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const runResponse = fetch(`${base}/runs`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({agentId:'starter-atlas',prompt:'test'})});
    for(let i=0;i<100&&!finishRun;i++) await new Promise(resolve=>setTimeout(resolve,10));
    assert.ok(finishRun,'executor started');
    assert.equal((await fetch(`${base}/agents/starter-atlas`,{method:'DELETE'})).status,409);
    finishRun({threadId:'test-thread',reply:'done'});
    assert.equal((await runResponse).status,200);
    assert.equal((await fetch(`${base}/agents/starter-atlas`,{method:'DELETE'})).status,200);
    assert.equal((await fetch(`${base}/agents/starter-atlas`,{method:'DELETE'})).status,404);
    assert.ok(store.agent('starter-nova'));
  } finally { finishRun?.({threadId:'test-thread',reply:'done'}); await new Promise(resolve=>server.close(resolve)); }
});
