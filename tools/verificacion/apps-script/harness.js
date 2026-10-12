#!/usr/bin/env node
// Differential test harness for google-apps-script.js.
//   node harness.js <path-to-script.js> <out.json>
// Loads the script into fresh vm contexts with in-memory Apps Script mocks (mocks.js), runs the
// deterministic scenario (scenario.js) and writes a stable JSON log: one record per step with the
// response, thrown error, Logger lines, lock/Drive/UrlFetch/trigger/property calls and a full snapshot
// of the spreadsheet. Exit code: 0 ok, 2 if any mock gap ("MOCK NOT IMPLEMENTED") was hit.
'use strict';

// Pin the process timezone BEFORE any Date is created, so `new Date(y, m, d)`-style local-time code
// inside the script behaves like Apps Script with the script timezone (and identically on any machine).
process.env.TZ = 'America/Argentina/Buenos_Aires';

const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { stableStringify } = require('./stable');

function main() {
  const [scriptPath, outPath] = process.argv.slice(2);
  if (!scriptPath || !outPath) {
    console.error('usage: node harness.js <path-to-script.js> <out.json>');
    process.exit(64);
  }
  if (new Date(2026, 0, 1).getTimezoneOffset() !== 180) {
    console.error('WARNING: could not pin TZ to America/Argentina/Buenos_Aires; local-time Date math may differ.');
  }
  const scriptSrc = fs.readFileSync(scriptPath, 'utf8');
  const mocksSrc = fs.readFileSync(path.join(__dirname, 'mocks.js'), 'utf8');
  const scenario = require('./scenario.js');

  const out = { meta: { script: path.basename(scriptPath), fixedNow: scenario.NOW, timezone: process.env.TZ }, steps: [] };
  const gaps = [];
  let index = 0;

  for (const phase of scenario.phases) {
    const ctx = vm.createContext({});
    vm.runInContext(mocksSrc, ctx, { filename: 'mocks.js' });
    ctx.__mock.init(JSON.stringify(phase.seed));
    let loadError = null;
    try {
      ctx.__mock.beginStep('{}');
      vm.runInContext(scriptSrc, ctx, { filename: path.basename(scriptPath) });
    } catch (err) {
      loadError = errString(err);
    }
    const loadRec = JSON.parse(ctx.__mock.endStep());
    out.steps.push(record(index++, phase.name, '(load script)', null, null, loadError, loadRec));

    for (const step of phase.steps) {
      ctx.__mock.beginStep(JSON.stringify(step.setup || {}));
      let result = null;
      let error = null;
      try {
        result = JSON.parse(ctx.__mock.invoke(step.fn, JSON.stringify(step.args || [])));
      } catch (err) {
        error = errString(err);
      }
      const rec = JSON.parse(ctx.__mock.endStep());
      const r = record(index++, phase.name, step.name, { fn: step.fn, args: step.args || [], setup: step.setup || null }, result, error, rec);
      out.steps.push(r);
      if (r.mockGaps.length) gaps.push(`[${r.index}] ${phase.name} / ${step.name}: ${r.mockGaps.join('; ')}`);
    }
  }

  fs.writeFileSync(outPath, stableStringify(out) + '\n');

  const errs = out.steps.filter((s) => s.error);
  console.log(`${out.steps.length} steps written to ${outPath}`);
  console.log(`${errs.length} step(s) threw: ` + errs.map((s) => `[${s.index}] ${s.step}`).join(', '));
  if (gaps.length) {
    console.log('MOCK GAPS:\n  ' + gaps.join('\n  '));
    process.exit(2);
  }
}

function errString(err) {
  if (err && typeof err === 'object' && 'message' in err) return (err.name || 'Error') + ': ' + err.message;
  return String(err);
}

function record(index, phase, step, call, result, error, rec) {
  return {
    index,
    phase,
    step,
    call,
    result,
    error,
    logs: rec.logs,
    locks: rec.locks,
    drive: rec.drive,
    urlFetch: rec.urlFetch,
    triggers: rec.triggers,
    propertyWrites: rec.propertyWrites,
    sheetOps: rec.sheetOps,
    mockGaps: rec.mockGaps,
    state: rec.state,
  };
}

main();
