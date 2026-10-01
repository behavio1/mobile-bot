import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
const script = resolve(import.meta.dirname, '../predefined-skills/android-app-builder/scripts/project.mjs');
function fixture(fn) { const root=mkdtempSync(join(tmpdir(),'app-project-'));try{fn(root)}finally{rmSync(root,{recursive:true,force:true})} }
function run(root,...args){return spawnSync(process.execPath,[script,...args],{cwd:root,encoding:'utf8'});}
test('repeat initialization preserves modified source and rejects a different app identity',()=>fixture(root=>{
 const first=run(root,'init','tasks','pl.owner.tasks','Zadania & notatki');assert.equal(first.status,0,first.stderr);
 const file=join(root,'projects/tasks/app-project/app/src/main/java/pl/owner/tasks/MainActivity.java');
 writeFileSync(file,'// user implementation');
 const again=run(root,'init','tasks','pl.owner.tasks','Zadania & notatki');assert.equal(again.status,0);assert.equal(JSON.parse(again.stdout).reused,true);assert.equal(readFileSync(file,'utf8'),'// user implementation');
 assert.notEqual(run(root,'init','tasks','pl.other.tasks','Inne').status,0);assert.equal(readFileSync(file,'utf8'),'// user implementation');
 assert.match(readFileSync(join(root,'projects/tasks/app-project/app/src/main/AndroidManifest.xml'),'utf8'),/Zadania &amp; notatki/);
 assert.match(readFileSync(join(root,'projects/tasks/app-project/app/build.gradle.kts'),'utf8'),/minSdk = 30/);
}));
test('invalid names and Mobile Bot identity cannot create a project',()=>fixture(root=>{
 for(const [slug,id] of [['../outside','pl.owner.tasks'],['tasks','one.behavio.mobilebotui.debug'],['tasks','bad-id']]) assert.notEqual(run(root,'init',slug,id,'Test').status,0);
 assert.equal(existsSync(join(root,'projects/tasks')),false);
}));
test('desktop environment cannot be reported as a verified Android phone',()=>fixture(root=>{
 assert.equal(run(root,'init','tasks','pl.owner.tasks','Test').status,0);
 const result=run(root,'check','tasks');assert.equal(result.status,0,result.stderr);const state=JSON.parse(result.stdout);assert.equal(state.deviceCompatible,false);assert.equal(state.ready,false);
 assert.equal(JSON.parse(run(root,'status','tasks').stdout).state,'created');
}));
test('a stopped build worker is reported as interrupted without deleting project files',()=>fixture(root=>{
 assert.equal(run(root,'init','tasks','pl.owner.tasks','Test').status,0);
 const dir=join(root,'projects/tasks');const meta=JSON.parse(readFileSync(join(dir,'project.json'),'utf8'));meta.state='building';meta.lastBuild={log:'reports/build.log'};writeFileSync(join(dir,'project.json'),JSON.stringify(meta));writeFileSync(join(dir,'build.lock'),'2147483647');writeFileSync(join(dir,'reports/build.log'),'partial build evidence');
 const state=JSON.parse(run(root,'status','tasks').stdout);assert.equal(state.state,'build_interrupted');assert.equal(readFileSync(join(dir,'reports/build.log'),'utf8'),'partial build evidence');assert.equal(state.lastBuild.log,'reports/build.log');
}));
