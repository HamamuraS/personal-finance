#!/usr/bin/env node
// node compare.js a.json b.json [--all] [--ignore=sheetOps,locks,styles,...] [--max-lines=80]
// Per-step diff of two harness outputs. Exit 0 if identical (after --ignore), 1 if different.
'use strict';
const fs = require('fs');

const argv = process.argv.slice(2);
const files = argv.filter((a) => !a.startsWith('--'));
const flag = (n) => argv.find((a) => a.startsWith('--' + n));
const ALL = !!flag('all');
const IGNORE = new Set(((flag('ignore=') || '').split('=')[1] || '').split(',').filter(Boolean));
const MAX = Number(((flag('max-lines=') || '').split('=')[1]) || 80);
if (files.length !== 2) { console.error('usage: node compare.js a.json b.json [--all] [--ignore=a,b] [--max-lines=N]'); process.exit(64); }
const A = JSON.parse(fs.readFileSync(files[0], 'utf8'));
const B = JSON.parse(fs.readFileSync(files[1], 'utf8'));

// ---------------------------------------------------------------- generic helpers
const J = (v) => JSON.stringify(v);
const eq = (a, b) => J(a) === J(b);
const fmt = (v) => { const s = v === undefined ? '(absent)' : J(v); return s.length > 220 ? s.slice(0, 217) + '...' : s; };
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const tagged = (v) => isObj(v) && Object.keys(v).length === 1 && Object.keys(v)[0][0] === '$';
function colLetter(c) { let s = ''; while (c > 0) { const m = (c - 1) % 26; s = String.fromCharCode(65 + m) + s; c = Math.floor((c - 1) / 26); } return s; }

/** LCS alignment of two arrays by key; returns ops [{op:'=',i,j}|{op:'-',i}|{op:'+',j}]. */
function align(a, b, key = J) {
  const ka = a.map(key), kb = b.map(key);
  const n = a.length, m = b.length;
  if (n * m > 4e6) { // too big: positional
    const ops = []; for (let i = 0; i < Math.max(n, m); i++) { if (i < n && i < m) ops.push(ka[i] === kb[i] ? { op: '=', i, j: i } : { op: '~', i, j: i }); else if (i < n) ops.push({ op: '-', i }); else ops.push({ op: '+', j: i }); } return ops;
  }
  const L = Array.from({ length: n + 1 }, () => new Int32Array(m + 1));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) L[i][j] = ka[i] === kb[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
  const ops = []; let i = 0, j = 0;
  while (i < n && j < m) {
    if (ka[i] === kb[j]) { ops.push({ op: '=', i, j }); i++; j++; }
    else if (L[i + 1][j] >= L[i][j + 1]) ops.push({ op: '-', i: i++ });
    else ops.push({ op: '+', j: j++ });
  }
  while (i < n) ops.push({ op: '-', i: i++ });
  while (j < m) ops.push({ op: '+', j: j++ });
  // pair adjacent runs of deletions/insertions as modifications
  const out = [];
  for (let k = 0; k < ops.length;) {
    if (ops[k].op === '=' || ops[k].op === '~') { out.push(ops[k++]); continue; }
    const dels = [], ins = [];
    while (k < ops.length && (ops[k].op === '-' || ops[k].op === '+')) { (ops[k].op === '-' ? dels : ins).push(ops[k]); k++; }
    const p = Math.min(dels.length, ins.length);
    for (let x = 0; x < p; x++) out.push({ op: '~', i: dels[x].i, j: ins[x].j });
    dels.slice(p).forEach((d) => out.push(d));
    ins.slice(p).forEach((d) => out.push(d));
  }
  return out;
}

function diffText(a, b, path, out) {
  const la = a.split('\n'), lb = b.split('\n');
  out.push(`~ ${path} (text, ${la.length} -> ${lb.length} lines):`);
  for (const o of align(la, lb)) {
    if (o.op === '-') out.push(`    - ${la[o.i]}`);
    else if (o.op === '+') out.push(`    + ${lb[o.j]}`);
    else if (o.op === '~') { out.push(`    - ${la[o.i]}`); out.push(`    + ${lb[o.j]}`); }
  }
}

function diff(a, b, path, out) {
  if (eq(a, b)) return;
  if (typeof a === 'string' && typeof b === 'string' && (a.includes('\n') || b.includes('\n'))) return diffText(a, b, path, out);
  if (Array.isArray(a) && Array.isArray(b)) {
    for (const o of align(a, b)) {
      if (o.op === '-') out.push(`- ${path}[${o.i}] = ${fmt(a[o.i])}`);
      else if (o.op === '+') out.push(`+ ${path}[${o.j}] = ${fmt(b[o.j])}`);
      else if (o.op === '~') diff(a[o.i], b[o.j], `${path}[${o.i === o.j ? o.i : o.i + '->' + o.j}]`, out);
    }
    return;
  }
  if (isObj(a) && isObj(b) && !tagged(a) && !tagged(b)) {
    const keys = [...new Set([...Object.keys(a), ...Object.keys(b)])].sort();
    for (const k of keys) {
      if (IGNORE.has(k) && path.endsWith('state')) continue;
      const p = path ? `${path}.${k}` : k;
      if (!(k in a)) out.push(`+ ${p} = ${fmt(b[k])}`);
      else if (!(k in b)) out.push(`- ${p} = ${fmt(a[k])}`);
      else diff(a[k], b[k], p, out);
    }
    return;
  }
  out.push(`~ ${path}: ${fmt(a)} -> ${fmt(b)}`);
}

// ---------------------------------------------------------------- sheet-aware diff of state
function diffSheets(sa, sb, out) {
  const na = sa.map((s) => s.name), nb = sb.map((s) => s.name);
  if (!eq(na, nb)) out.push(`~ sheet list: ${J(na)} -> ${J(nb)}`);
  const byA = Object.fromEntries(sa.map((s) => [s.name, s])), byB = Object.fromEntries(sb.map((s) => [s.name, s]));
  for (const name of [...new Set([...na, ...nb])]) {
    const x = byA[name], y = byB[name];
    if (!x) { out.push(`+ sheet "${name}" (${y.values.length} rows)`); y.values.forEach((r, i) => out.push(`    + row ${i + 1}: ${fmt(r)}`)); continue; }
    if (!y) { out.push(`- sheet "${name}" (${x.values.length} rows)`); continue; }
    for (const k of ['maxRows', 'maxCols', 'frozenRows']) if (x[k] !== y[k]) out.push(`~ "${name}".${k}: ${x[k]} -> ${y[k]}`);
    for (const o of align(x.values, y.values)) {
      if (o.op === '-') out.push(`- "${name}" row ${o.i + 1} (A) removed: ${fmt(x.values[o.i])}`);
      else if (o.op === '+') out.push(`+ "${name}" row ${o.j + 1} (B) added: ${fmt(y.values[o.j])}`);
      else if (o.op === '~') {
        const ra = x.values[o.i], rb = y.values[o.j];
        const rowLabel = o.i === o.j ? `${o.j + 1}` : `${o.i + 1}->${o.j + 1}`;
        for (let c = 0; c < Math.max(ra.length, rb.length); c++) {
          if (!eq(ra[c], rb[c])) out.push(`~ "${name}"!${colLetter(c + 1)}${rowLabel}: ${fmt(ra[c])} -> ${fmt(rb[c])}`);
        }
      }
    }
    if (!IGNORE.has('numberFormats')) diff(x.numberFormats, y.numberFormats, `"${name}".numberFormats`, out);
    if (!IGNORE.has('styles')) diff(x.styles, y.styles, `"${name}".styles`, out);
  }
}

const SECTIONS = ['call', 'result', 'error', 'logs', 'locks', 'drive', 'urlFetch', 'triggers', 'propertyWrites', 'sheetOps', 'mockGaps', 'state'];

function diffStep(a, b) {
  const sec = {};
  for (const k of SECTIONS) {
    if (IGNORE.has(k)) continue;
    const out = [];
    if (k === 'state') {
      diffSheets(a.state.sheets, b.state.sheets, out);
      if (!IGNORE.has('properties')) diff(a.state.properties, b.state.properties, 'properties', out);
      if (!IGNORE.has('triggers')) diff(a.state.triggers, b.state.triggers, 'triggers(state)', out);
    } else diff(a[k], b[k], k, out);
    if (out.length) sec[k] = out;
  }
  return sec;
}

// ---------------------------------------------------------------- main
let differing = 0, shownDetail = 0;
const n = Math.max(A.steps.length, B.steps.length);
if (A.steps.length !== B.steps.length) console.log(`! step count differs: ${A.steps.length} vs ${B.steps.length}`);
for (let i = 0; i < n; i++) {
  const a = A.steps[i], b = B.steps[i];
  if (!a || !b) { differing++; console.log(`[${i}] only in ${a ? 'A' : 'B'}: ${(a || b).phase} / ${(a || b).step}`); continue; }
  const sec = diffStep(a, b);
  if (a.step !== b.step || a.phase !== b.phase) sec.stepName = [`~ ${a.phase}/${a.step} -> ${b.phase}/${b.step}`];
  const keys = Object.keys(sec);
  if (!keys.length) continue;
  differing++;
  const header = `[${i}] ${a.phase} / ${a.step}  -- differs in: ${keys.map((k) => `${k}(${sec[k].length})`).join(', ')}`;
  if (shownDetail === 0 || ALL) {
    shownDetail++;
    console.log((shownDetail === 1 ? '=== FIRST DIFFERING STEP ===\n' : '\n') + header);
    let lines = 0;
    for (const k of keys) {
      console.log(`  -- ${k}`);
      for (const l of sec[k]) {
        if (lines++ >= MAX) break;
        console.log('     ' + l.replace(/\n/g, '\n     '));
      }
      if (lines >= MAX) { console.log(`     ... (truncated, use --max-lines)`); break; }
    }
    if (!ALL) console.log('\n=== other differing steps (use --all for details) ===');
  } else console.log(header);
}
if (!differing) { console.log(`IDENTICAL: ${n} steps match${IGNORE.size ? ' (ignoring ' + [...IGNORE].join(',') + ')' : ''}.`); process.exit(0); }
console.log(`\n${differing} of ${n} steps differ.`);
process.exit(1);
