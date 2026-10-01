import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync, lstatSync } from 'node:fs';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';

const bases = ['heroes', 'office', 'animals', 'influencers'];
const roles = ['primary', 'container', 'accent', 'background', 'surface', 'raised', 'ink', 'muted'];
export function validateTheme(value: unknown): Record<string, any> {
  const t = value as Record<string, any>;
  const fail = (reason: string): never => { throw new Error(`invalid_theme:${reason}`); };
  if (!t || t.schemaVersion !== 1 || !/^custom-[a-z0-9-]{1,48}$/.test(t.id)) fail('identity');
  for (const key of ['name', 'description', 'prompt']) if (typeof t[key] !== 'string' || !t[key].trim() || t[key].length > (key === 'prompt' ? 8000 : 160)) fail(key);
  if (!bases.includes(t.artworkTheme)) fail('artworkTheme');
  for (const mode of ['light', 'dark']) {
    const p = t[mode];
    for (const role of roles) if (!/^#[0-9a-fA-F]{6}$/.test(p?.[role])) fail(`${mode}.${role}`);
    const pairs = [[p.ink,p.background],[p.ink,p.surface],[p.ink,p.container],[p.muted,p.raised],
      [mode === 'dark' ? '#201923' : '#FFFFFF',p.primary],['#201923',p.accent]];
    for (const [fg,bg] of pairs) if (contrast(fg,bg) < 4.5) fail(`contrast:${mode}:${fg}:${bg}`);
  }
  if (!['sans','serif','mono'].includes(t.typography?.family)) fail('font');
  number(t.typography.weight, 400, 900); number(t.typography.tracking, -0.5, 1);
  if (t.typography.weight % 100 !== 0) fail('weight');
  for (const key of ['small','medium','large']) number(t.shapes?.[key], 0, 36);
  if (!['FOIL','EDITORIAL','SOFT','POP'].includes(t.card?.treatment)) fail('card');
  for (const [key,min,max] of [['radius',0,36],['border',0,3],['elevation',0,8],['inset',0,8],['gridGap',8,24]] as const) number(t.card[key],min,max);
  return t;
}
function number(v: unknown,min:number,max:number) { if (typeof v !== 'number' || !Number.isFinite(v) || v < min || v > max) throw new Error('invalid_theme:number'); }
function luminance(hex: string) { const rgb = hex.slice(1).match(/../g)!.map(c=>parseInt(c,16)/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4);return rgb[0]!*.2126+rgb[1]!*.7152+rgb[2]!*.0722; }
function contrast(a:string,b:string) { const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
export function saveTheme(root: string, input: unknown) {
  const theme = validateTheme(input);
  mkdirSync(root,{recursive:true,mode:0o700});
  const target=join(root,`${theme.id}.json`), temporary=join(root,`.${randomUUID()}.tmp`);
  writeFileSync(temporary,JSON.stringify(theme),{mode:0o600}); renameSync(temporary,target);
  return theme;
}
export function listThemes(root: string) {
  if (!existsSync(root)) return [];
  return readdirSync(root).filter(n=>/^custom-[a-z0-9-]+\.json$/.test(n)).slice(0,50).flatMap(n=>{
    try { const path=join(root,n); if(lstatSync(path).isSymbolicLink() || lstatSync(path).size>65536)return [];
      return [validateTheme(JSON.parse(readFileSync(path,'utf8')))];
    } catch { process.stderr.write(`theme_rejected file=${n}\n`);return []; }
  });
}
