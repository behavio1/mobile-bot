import {readFileSync,writeFileSync} from 'node:fs';
const [operation,path]=process.argv.slice(2);
if(!['validate','publish'].includes(operation)||!path) throw new Error('Użycie: theme.mjs validate|publish theme.json');
const theme=JSON.parse(readFileSync(path,'utf8'));
const auth={Authorization:`Bearer ${readFileSync(`${process.env.HOME??'.'}/.mobile-bot-ui/device-token`,'utf8').trim()}`};
console.log(`theme_${operation}_start id=${theme.id}`);
const response=await fetch(`http://127.0.0.1:8767/themes${operation==='validate'?'/validate':''}`,{
 method:'POST',headers:{...auth,'Content-Type':'application/json'},body:JSON.stringify({theme}),signal:AbortSignal.timeout(15000)});
const result=await response.json();
if(!response.ok) throw new Error(JSON.stringify(result));
if(operation==='publish') {
 const workspace=await fetch('http://127.0.0.1:8767/workspace',{headers:auth,signal:AbortSignal.timeout(15000)}).then(r=>r.json());
 const saved=workspace.customThemes.find(t=>t.id===theme.id);
 if(JSON.stringify(saved)!==JSON.stringify(theme)) throw new Error('theme_readback_mismatch');
 writeFileSync(`${path}.report.md`,`# Motyw ${theme.name}\n\nZapis i pełny odczyt paczki: PASS.\nId: ${theme.id}\nGrafiki: istniejący zestaw ${theme.artworkTheme}.\nData: ${new Date().toISOString()}\n`);
}
console.log(`theme_${operation}_ok id=${theme.id}`);
