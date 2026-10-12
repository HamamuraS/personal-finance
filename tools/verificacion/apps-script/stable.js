// Deterministic, diff-friendly JSON: object keys sorted, arrays of scalars on one line.
'use strict';

function isScalar(v) { return v === null || typeof v !== 'object'; }
function isSmall(v) { // scalars or tiny tagged objects like {"$date": "..."}
  if (isScalar(v)) return true;
  if (!Array.isArray(v)) { const k = Object.keys(v); return k.length === 1 && k[0][0] === '$' && isScalar(v[k[0]]); }
  return false;
}

function stableStringify(v, indent = '') {
  if (isScalar(v)) return JSON.stringify(v === undefined ? null : v);
  const next = indent + ' ';
  if (Array.isArray(v)) {
    if (v.length === 0) return '[]';
    if (v.every(isSmall)) return '[' + v.map((x) => stableStringify(x, '')).join(', ') + ']';
    return '[\n' + v.map((x) => next + stableStringify(x, next)).join(',\n') + '\n' + indent + ']';
  }
  const keys = Object.keys(v).sort();
  if (keys.length === 0) return '{}';
  if (isSmall(v)) return '{' + JSON.stringify(keys[0]) + ': ' + JSON.stringify(v[keys[0]]) + '}';
  return '{\n' + keys.map((k) => next + JSON.stringify(k) + ': ' + stableStringify(v[k], next)).join(',\n') + '\n' + indent + '}';
}

module.exports = { stableStringify };
