import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {validateTheme,saveTheme,listThemes} from '../dist/themePackages.js';
const theme=()=>({schemaVersion:1,id:'custom-test',name:'Test',description:'Motyw testowy',prompt:'Sprawdzenie kontraktu',artworkTheme:'heroes',
 light:{primary:'#173C37',container:'#D5EAE4',accent:'#F2C85B',background:'#F7F6F2',surface:'#FFFEFA',raised:'#E9EFEB',ink:'#17211F',muted:'#4E625C'},
 dark:{primary:'#9FD4CA',container:'#28504A',accent:'#F2C85B',background:'#0C1915',surface:'#172B23',raised:'#284035',ink:'#F0F5F2',muted:'#BBCDC4'},
 typography:{family:'sans',weight:700,tracking:0},shapes:{small:8,medium:16,large:24},card:{treatment:'FOIL',radius:18,border:1,elevation:4,inset:5,gridGap:14}});
test('publishes and restores exact complete theme without duplicate on retry',()=>{
 const root=mkdtempSync(join(tmpdir(),'themes-'));try{
 saveTheme(root,theme());saveTheme(root,theme());assert.deepEqual(listThemes(root),[theme()]);
 }finally{rmSync(root,{recursive:true,force:true});}
});
test('rejects bad contrast, missing mode, factory override and invalid card tokens',()=>{
 for(const change of [t=>t.light.ink=t.light.surface,t=>delete t.dark,t=>t.id='heroes',t=>t.id='../../bad',t=>t.card.radius=-1,t=>t.artworkTheme='remote',t=>t.typography.family='javascript']){
  const t=theme();change(t);assert.throws(()=>validateTheme(t));
 }
});
