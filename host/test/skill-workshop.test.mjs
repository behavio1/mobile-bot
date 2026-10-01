import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, symlinkSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { SkillWorkshop, readSkillPackage } from '../dist/skillWorkshop.js';
import { createHostServer, SqliteWorkspaceStore, materializeAgentWorkspace } from '../dist/index.js';

const result = { status: 'ready', name: 'Suma wydatków', summary: 'Podsumowuje dane liczbowe z pliku.', question: '', evidence: 'Sprawdzono wynik na próbce oraz pusty zbiór.', toolCount: 1 };
const write = (path, text) => { mkdirSync(join(path, '..'), { recursive: true }); writeFileSync(path, text); };
async function runner(step, dir) {
  const { skillId } = JSON.parse(readFileSync(join(dir, 'request.json')));
  if (step === 'research') write(join(dir, 'research.md'), '# Źródła\nhttps://nodejs.org/api/fs.html\nSprawdzono odczyt pliku w Node.');
  if (step === 'build') {
    write(join(dir, 'output/skill/SKILL.md'), `---\nname: ${skillId}\ndescription: Sumuj liczby z tablicy JSON i zapisz wynik.\n---\n# Suma wydatków\n\nUżyj [instrukcji](references/input.md). Uruchom node scripts/sum.mjs ze ścieżką pliku JSON. Sprawdź wynik przez niezależne dodanie wartości.\n`);
    write(join(dir, 'output/skill/references/input.md'), '# Format\nTablica liczb; pusta tablica daje zero.');
    write(join(dir, 'output/skill/scripts/sum.mjs'), 'import {readFileSync} from "node:fs"; console.log(JSON.parse(readFileSync(process.argv[2])).reduce((a,b)=>a+b,0));');
  }
  if (step === 'verify') write(join(dir, 'verification.md'), '# Weryfikacja\nPróbka [2,3] daje 5, [] daje 0.');
  return result;
}
async function settle(workshop, id) {
  for (let i=0;i<200;i++) {
    const job=workshop.get(id);
    if (!['queued','researching','building','validating'].includes(job.status)) return job;
    await new Promise(r=>setTimeout(r,5));
  }
  assert.fail('job did not finish');
}
function setup(t, worker=runner) {
  const root=mkdtempSync(join(tmpdir(),'skill-workshop-'));
  const events=[];
  const workshop=new SkillWorkshop(root,worker,(event,fields)=>events.push({event,...fields}));
  t.after(()=>{workshop.close();rmSync(root,{recursive:true,force:true});});
  return {root,workshop,events};
}

test('build persists complete package, survives restart, and idempotency does not duplicate', async t=>{
  const {root,workshop,events}=setup(t);
  const requestId=randomUUID();
  const job=workshop.create('starter-atlas',requestId,'Podsumowuj liczby z JSON.');
  assert.equal(workshop.create('starter-atlas',requestId,'Podsumowuj liczby z JSON.').id,job.id);
  assert.throws(()=>workshop.create('starter-atlas',requestId,'Inne wymaganie użytkownika.'),/skill_request_conflict/);
  assert.equal((await settle(workshop,job.id)).status,'ready');
  const pack=workshop.skill(job.skillId);
  assert.ok(pack.files['scripts/sum.mjs']);
  assert.ok(pack.files['references/input.md']);
  assert.equal(events.filter(e=>e.event==='skill_build.created').length,1);
  assert.ok(!JSON.stringify(events).includes('Podsumowuj'));
  workshop.close();
  const restored=new SkillWorkshop(root,runner,()=>{});
  assert.equal(restored.get(job.id).status,'ready');
  assert.equal(restored.skill(job.skillId).hash,pack.hash);
  restored.close();
});

test('questions, retry and validation failure never publish an incomplete skill',async t=>{
  let asked=false;
  const {workshop}=setup(t,async(step,dir)=>{
    if(!asked){asked=true;return {...result,status:'needs_input',question:'Jaki format danych?',toolCount:0};}
    return runner(step,dir);
  });
  const job=workshop.create('starter-atlas',randomUUID(),'Podsumuj dane użytkownika.');
  assert.equal((await settle(workshop,job.id)).status,'needs_input');
  assert.equal(workshop.definitions().length,0);
  assert.throws(()=>workshop.resume(job.id,''),/skill_answer_required/);
  workshop.resume(job.id,'Tablica JSON.');
  assert.equal((await settle(workshop,job.id)).status,'ready');
  assert.deepEqual(job.answers,['Tablica JSON.']);
});

test('failed verification, no tool execution, missing resources and changed package stay unpublished',async t=>{
  for(const mode of ['failed','no-tools','missing','changed']) {
    await t.test(mode,async t=>{
      const {workshop}=setup(t,async(step,dir)=>{
        const r=await runner(step,dir);
        if(step==='build'&&mode==='missing')rmSync(join(dir,'output/skill/references/input.md'));
        if(step==='verify'&&mode==='changed')write(join(dir,'output/skill/extra.md'),'changed');
        if(step==='verify'&&mode==='failed')return {...r,status:'failed',evidence:'Błędny wynik.'};
        return step==='verify'&&mode==='no-tools'?{...r,toolCount:0}:r;
      });
      const job=workshop.create('starter-atlas',randomUUID(),'Podsumowuj liczby z JSON.');
      assert.equal((await settle(workshop,job.id)).status,'failed');
      assert.equal(workshop.definitions().length,0);
    });
  }
});

test('package validation rejects symlinks and references escaping package',t=>{
  const {root}=setup(t);const dir=join(root,'package');mkdirSync(dir);
  write(join(dir,'SKILL.md'),'---\nname: example\ndescription: Samodzielna umiejętność testowa.\n---\n# Opis\n'+ 'Wykonaj zadanie i sprawdź rzeczywisty wynik. '.repeat(5));
  symlinkSync('/etc/hosts',join(dir,'other.md'));
  assert.throws(()=>readSkillPackage(dir,'example'),/invalid_skill_file/);
  rmSync(join(dir,'other.md'));
  writeFileSync(join(dir,'SKILL.md'),readFileSync(join(dir,'SKILL.md'),'utf8')+'\n[Wymagane](../other.md)');
  assert.throws(()=>readSkillPackage(dir,'example'),/invalid_skill_reference/);
});

test('interrupted job is recoverable and does not become ready on restart',async t=>{
  const {root,workshop}=setup(t,async()=>new Promise(()=>{}));
  const job=workshop.create('starter-atlas',randomUUID(),'Podsumowuj liczby z JSON.');
  const restored=new SkillWorkshop(root,runner,()=>{});
  assert.equal(restored.get(job.id).status,'interrupted');
  assert.equal(restored.definitions().length,0);
  restored.resume(job.id,'');
  assert.equal((await settle(restored,job.id)).status,'ready');
  restored.close();
});

test('HTTP build to shared catalog to SQLite assignment to actual agent files; preserved local changes',async t=>{
  const {root,workshop}=setup(t);
  const db=join(root,'state.sqlite');const store=new SqliteWorkspaceStore(db,workshop);
  const workspaceRoot=join(root,'agents');
  const server=createHostServer(()=>({installed:true,authenticated:true,version:'test'}),async()=>assert.fail('unexpected business run'),store,{
    skillWorkshop:workshop,agentWorkspaceRoot:workspaceRoot,
    conversationStarterGenerator:async agents=>({startersByAgentId:Object.fromEntries(agents.map(a=>[a.id,['Podsumuj dane z pliku','Sprawdź format danych','Porównaj dwa zestawy','Wyjaśnij wynik obliczeń']])),toolEventCount:0}),
  });
  server.listen(0,'127.0.0.1');await once(server,'listening');t.after(()=>server.close());
  const base=`http://127.0.0.1:${server.address().port}`;
  const post=(path,body,method='POST')=>fetch(base+path,{method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  const res=await post('/skill-builds',{agentId:'starter-atlas',requestId:randomUUID(),description:'Podsumowuj liczby z JSON.'});
  assert.equal(res.status,202);const job=(await res.json()).build;await settle(workshop,job.id);
  const snapshot=await (await fetch(base+'/workspace')).json();
  assert.ok(snapshot.skillDefinitions.some(s=>s.id===job.skillId));
  assert.ok(!snapshot.skillDefinitions.some(s=>s.id==='skill-builder'));
  assert.equal((await post('/agents/starter-atlas/skills',{skillIds:[job.skillId]},'PUT')).status,200);
  const local=join(workspaceRoot,'starter-atlas','.agents/skills',job.skillId,'SKILL.md');
  assert.ok(readFileSync(local,'utf8').includes('mobile-bot-local-learning'));
  assert.ok(existsSync(join(dirname(local),'scripts/sum.mjs')));
  writeFileSync(local,readFileSync(local,'utf8')+'\nLocal learned rule.');
  const restored=new SqliteWorkspaceStore(db,workshop);const agent=restored.agent('starter-atlas');
  materializeAgentWorkspace(agent,join(workspaceRoot,'starter-atlas'));
  assert.ok(readFileSync(local,'utf8').includes('Local learned rule.'));
  assert.equal(agent.skills[0].id,job.skillId);
  assert.ok(agent.skills[0].files['scripts/sum.mjs']);
  assert.ok(!workshop.skill(job.skillId).files['SKILL.md'].includes('Local learned rule.'));
  const detail=await(await fetch(base+'/skills/'+job.skillId)).json();assert.ok(detail.content.includes('name: '+job.skillId));
});
