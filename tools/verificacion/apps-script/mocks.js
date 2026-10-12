// mocks.js -- evaluated INSIDE the vm context (same realm as the Apps Script source), so every Date,
// Array and Error the script sees is a native of its own realm (`instanceof Date` keeps working).
// Exposes the Apps Script globals plus a control object `__mock` used by harness.js.
// Everything crossing the vm boundary goes as JSON strings.
(function (G) {
  'use strict';

  const RealDate = Date;
  const KIND = Symbol.for('mock.kind');
  const DEFAULT_TZ = 'America/Argentina/Buenos_Aires';
  const TZ_OFFSET_MIN = { 'America/Argentina/Buenos_Aires': -180, 'GMT': 0, 'UTC': 0, 'Etc/GMT': 0, 'Etc/UTC': 0 };

  // ------------------------------------------------------------------ state
  const S = {
    defaultNowMs: 0,
    nowMs: 0,
    rng: 1,
    lockBusy: false,
    fetchQueue: [],
    defaultFetch: null,
    failingFolders: {},
    sharingFailFolders: {},
    fileCounter: 0,
    triggerCounter: 0,
    uuidCounter: 0,
    triggers: [],
    props: {},
    ss: null,
    rec: null,
  };

  function newRec() {
    return { logs: [], locks: [], drive: [], urlFetch: [], triggers: [], sheetOps: [], propertyWrites: [], mockGaps: [] };
  }
  S.rec = newRec();

  function gap(name) {
    const msg = 'MOCK NOT IMPLEMENTED: ' + name;
    S.rec.mockGaps.push(msg);
    throw new Error(msg);
  }

  /** Wraps an API object so that any unknown property access throws a named error. */
  function strict(name, api) {
    Object.defineProperty(api, KIND, { value: name, enumerable: false });
    return new Proxy(api, {
      get(t, p, r) {
        if (typeof p === 'symbol' || p in t) return Reflect.get(t, p, r);
        return gap(name + '.' + String(p));
      },
      set(t, p) { return gap('assignment to ' + name + '.' + String(p)); },
    });
  }

  // ------------------------------------------------------------------ frozen clock + seeded random
  function FakeDate(...args) {
    if (!new.target) return new RealDate(S.nowMs).toString();
    if (args.length === 0) return new RealDate(S.nowMs);
    return new RealDate(...args);
  }
  FakeDate.prototype = RealDate.prototype;
  FakeDate.now = function () { return S.nowMs; };
  FakeDate.UTC = RealDate.UTC;
  FakeDate.parse = RealDate.parse;
  G.Date = FakeDate;

  function rand() { // mulberry32
    let t = (S.rng = (S.rng + 0x6D2B79F5) | 0);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }
  Math.random = rand;

  // ------------------------------------------------------------------ helpers
  function isDate(v) { return v instanceof RealDate; }
  function pad(v, n) { return String(v).padStart(n, '0'); }
  function colLetter(c) { let s = ''; while (c > 0) { const m = (c - 1) % 26; s = String.fromCharCode(65 + m) + s; c = Math.floor((c - 1) / 26); } return s; }
  function colNumber(letters) { let n = 0; for (const ch of letters.toUpperCase()) n = n * 26 + (ch.charCodeAt(0) - 64); return n; }
  function a1(r, c) { return colLetter(c) + r; }

  function ser(v) { // deterministic serialization of a value from this realm
    if (v === undefined) return { $undefined: true };
    if (v === null) return null;
    if (isDate(v)) return { $date: isNaN(v.getTime()) ? 'Invalid Date' : v.toISOString() };
    if (typeof v === 'number') {
      if (Number.isNaN(v)) return { $num: 'NaN' };
      if (!Number.isFinite(v)) return { $num: String(v) };
      return v;
    }
    if (typeof v === 'string' || typeof v === 'boolean') return v;
    if (typeof v === 'function') return { $function: v.name || 'anonymous' };
    if (Array.isArray(v)) return v.map(ser);
    if (typeof v === 'object') {
      if (v[KIND]) return { $mock: v[KIND] };
      const o = {};
      Object.keys(v).sort().forEach(function (k) { o[k] = ser(v[k]); });
      return o;
    }
    return { $other: String(v) };
  }

  function revive(v) { // seed / args: {$date: iso} -> Date, {$undefined:true} -> undefined
    if (Array.isArray(v)) return v.map(revive);
    if (v && typeof v === 'object') {
      if (Object.keys(v).length === 1 && typeof v.$date === 'string') return new RealDate(v.$date);
      if (v.$undefined === true) return undefined;
      const o = {};
      Object.keys(v).forEach(function (k) { o[k] = revive(v[k]); });
      return o;
    }
    return v;
  }

  // ------------------------------------------------------------------ Utilities
  function formatDate(d, tz, pattern) {
    if (!isDate(d)) throw new Error('Exception: Invalid argument: date (Utilities.formatDate got ' + typeof d + ')');
    if (!(tz in TZ_OFFSET_MIN)) gap('Utilities.formatDate timezone "' + tz + '"');
    if (typeof pattern !== 'string') throw new Error('Exception: Invalid argument: format');
    const t = new RealDate(d.getTime() + TZ_OFFSET_MIN[tz] * 60000);
    const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    let out = '', i = 0;
    while (i < pattern.length) {
      const ch = pattern[i];
      if (ch === "'") {
        if (pattern[i + 1] === "'") { out += "'"; i += 2; continue; }
        let j = i + 1, lit = '';
        while (j < pattern.length && pattern[j] !== "'") lit += pattern[j++];
        out += lit; i = j + 1; continue;
      }
      if (/[A-Za-z]/.test(ch)) {
        let j = i; while (j < pattern.length && pattern[j] === ch) j++;
        const n = j - i;
        switch (ch) {
          case 'y': out += n === 2 ? pad(t.getUTCFullYear() % 100, 2) : pad(t.getUTCFullYear(), n); break;
          case 'M': if (n > 2) gap('Utilities.formatDate pattern "MMM"'); out += pad(t.getUTCMonth() + 1, n); break;
          case 'd': out += pad(t.getUTCDate(), n); break;
          case 'H': out += pad(t.getUTCHours(), n); break;
          case 'm': out += pad(t.getUTCMinutes(), n); break;
          case 's': out += pad(t.getUTCSeconds(), n); break;
          case 'S': out += pad(t.getUTCMilliseconds(), n); break;
          case 'u': { const w = t.getUTCDay(); out += pad(w === 0 ? 7 : w, n); break; }
          case 'E': out += n >= 4 ? DAYS[t.getUTCDay()] : DAYS[t.getUTCDay()].substring(0, 3); break;
          default: gap('Utilities.formatDate pattern letter "' + ch + '"');
        }
        i = j; continue;
      }
      out += ch; i++;
    }
    return out;
  }

  const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  function base64Decode(s) {
    const clean = String(s).replace(/\s+/g, '');
    if (!/^[A-Za-z0-9+/]*={0,2}$/.test(clean) || clean.replace(/=+$/, '').length % 4 === 1) {
      throw new Error('Exception: Could not decode string.');
    }
    const body = clean.replace(/=+$/, '');
    const bytes = [];
    let buf = 0, bits = 0;
    for (const ch of body) {
      buf = (buf << 6) | B64.indexOf(ch); bits += 6;
      if (bits >= 8) { bits -= 8; const b = (buf >> bits) & 0xff; bytes.push(b > 127 ? b - 256 : b); }
    }
    return bytes; // Apps Script returns signed bytes (Java byte[])
  }
  function fnv(bytes) { let h = 0x811c9dc5; for (const b of bytes) { h ^= (b & 0xff); h = Math.imul(h, 0x01000193) >>> 0; } return pad(h.toString(16), 8); }

  function makeBlob(data, contentType, name) {
    let bytes;
    if (typeof data === 'string') bytes = Array.from(data).map(function (c) { return c.charCodeAt(0) & 0xff; });
    else if (Array.isArray(data)) bytes = data.slice();
    else gap('Utilities.newBlob with data of type ' + typeof data);
    let blobName = name === undefined ? null : name;
    const api = {
      getBytes: function () { return bytes.slice(); },
      getContentType: function () { return contentType === undefined ? null : contentType; },
      getName: function () { return blobName; },
      setName: function (n) { blobName = n; return self; },
    };
    const self = strict('Blob', api);
    return self;
  }

  G.Utilities = strict('Utilities', {
    formatDate: formatDate,
    base64Decode: base64Decode,
    newBlob: makeBlob,
    getUuid: function () { S.uuidCounter++; return '00000000-0000-4000-8000-' + pad(S.uuidCounter, 12); },
  });

  // ------------------------------------------------------------------ Logger / console
  function logLine(level, args) {
    let s;
    if (args.length > 1 && typeof args[0] === 'string' && args[0].indexOf('%s') !== -1) {
      let k = 1; s = args[0].replace(/%s/g, function () { return k < args.length ? String(args[k++]) : '%s'; });
    } else {
      s = args.map(function (a) { return typeof a === 'string' ? a : JSON.stringify(ser(a)); }).join(' ');
    }
    S.rec.logs.push(level ? '[' + level + '] ' + s : s);
  }
  const loggerApi = { log: function () { logLine('', Array.from(arguments)); return G.Logger; } };
  G.Logger = strict('Logger', loggerApi);
  G.console = strict('console', {
    log: function () { logLine('console.log', Array.from(arguments)); },
    info: function () { logLine('console.info', Array.from(arguments)); },
    warn: function () { logLine('console.warn', Array.from(arguments)); },
    error: function () { logLine('console.error', Array.from(arguments)); },
  });

  // ------------------------------------------------------------------ Session
  G.Session = strict('Session', { getScriptTimeZone: function () { return DEFAULT_TZ; } });

  // ------------------------------------------------------------------ ContentService
  G.ContentService = strict('ContentService', {
    MimeType: strict('ContentService.MimeType', { JSON: 'JSON', TEXT: 'TEXT', CSV: 'CSV', JAVASCRIPT: 'JAVASCRIPT', XML: 'XML', ICAL: 'ICAL', VCARD: 'VCARD', RSS: 'RSS', ATOM: 'ATOM' }),
    createTextOutput: function (content) {
      let text = content === undefined ? '' : String(content);
      let mime = 'TEXT';
      const api = {
        getContent: function () { return text; },
        setContent: function (c) { text = String(c); return self; },
        append: function (c) { text += String(c); return self; },
        getMimeType: function () { return mime; },
        setMimeType: function (m) {
          if (m === undefined || m === null) throw new Error('Exception: Invalid argument: mimeType');
          mime = m; return self;
        },
      };
      Object.defineProperty(api, '__textOutput', { value: true });
      const self = strict('TextOutput', api);
      return self;
    },
  });

  // ------------------------------------------------------------------ LockService
  G.LockService = strict('LockService', {
    getScriptLock: function () {
      S.rec.locks.push('getScriptLock');
      let held = false;
      const self = strict('Lock', {
        waitLock: function (ms) {
          S.rec.locks.push('waitLock(' + ms + ')');
          if (S.lockBusy) throw new Error('Exception: Lock timeout: another process was holding the lock for too long.');
          held = true;
        },
        tryLock: function (ms) {
          S.rec.locks.push('tryLock(' + ms + ')');
          if (S.lockBusy) return false;
          held = true; return true;
        },
        releaseLock: function () { S.rec.locks.push('releaseLock'); held = false; },
        hasLock: function () { return held; },
      });
      return self;
    },
    getDocumentLock: function () { return gap('LockService.getDocumentLock'); },
    getUserLock: function () { return gap('LockService.getUserLock'); },
  });

  // ------------------------------------------------------------------ PropertiesService
  const scriptProps = strict('Properties', {
    getProperty: function (k) { return Object.prototype.hasOwnProperty.call(S.props, k) ? S.props[k] : null; },
    setProperty: function (k, v) { S.props[k] = String(v); S.rec.propertyWrites.push({ set: k, value: String(v) }); return scriptProps; },
    deleteProperty: function (k) { delete S.props[k]; S.rec.propertyWrites.push({ delete: k }); return scriptProps; },
    getProperties: function () { return Object.assign({}, S.props); },
    getKeys: function () { return Object.keys(S.props).sort(); },
  });
  G.PropertiesService = strict('PropertiesService', {
    getScriptProperties: function () { return scriptProps; },
  });

  // ------------------------------------------------------------------ ScriptApp (triggers)
  function makeTrigger(t) {
    return strict('Trigger', {
      getHandlerFunction: function () { return t.handler; },
      getUniqueId: function () { return t.id; },
      getEventType: function () { return 'CLOCK'; },
      getTriggerSource: function () { return 'CLOCK'; },
    });
  }
  G.ScriptApp = strict('ScriptApp', {
    newTrigger: function (handler) {
      const cfg = { handler: String(handler) };
      const clock = strict('ClockTriggerBuilder', {
        atHour: function (h) { cfg.atHour = h; return clock; },
        nearMinute: function (m) { cfg.nearMinute = m; return clock; },
        everyDays: function (n) { cfg.everyDays = n; return clock; },
        everyHours: function (n) { cfg.everyHours = n; return clock; },
        everyMinutes: function (n) { cfg.everyMinutes = n; return clock; },
        inTimezone: function (tz) { cfg.inTimezone = tz; return clock; },
        create: function () {
          S.triggerCounter++;
          const t = Object.assign({ id: 'trigger-' + S.triggerCounter }, cfg);
          S.triggers.push(t);
          S.rec.triggers.push({ create: ser(cfg) });
          return makeTrigger(t);
        },
      });
      return strict('TriggerBuilder', { timeBased: function () { cfg.type = 'timeBased'; return clock; } });
    },
    getProjectTriggers: function () { return S.triggers.map(makeTrigger); },
    deleteTrigger: function (trig) {
      const id = trig.getUniqueId();
      const idx = S.triggers.findIndex(function (t) { return t.id === id; });
      if (idx === -1) throw new Error('Exception: Trigger not found: ' + id);
      S.rec.triggers.push({ delete: S.triggers[idx].handler, id: id });
      S.triggers.splice(idx, 1);
    },
  });

  // ------------------------------------------------------------------ UrlFetchApp
  function defaultGeminiResponse() {
    return {
      code: 200,
      body: {
        candidates: [{
          content: { parts: [
            { text: 'Pensando en qué decir...', thought: true },
            { text: '```json\n{"Santiago": "¿Qué tal ese café Martínez de hoy? ☕", "Rocío": "Que hoy te salga todo redondo"}\n```' },
          ] },
          finishReason: 'STOP',
        }],
      },
    };
  }
  G.UrlFetchApp = strict('UrlFetchApp', {
    fetch: function (url, params) {
      params = params || {};
      const entry = { url: String(url) };
      Object.keys(params).sort().forEach(function (k) {
        if (k === 'payload' && typeof params.payload === 'string') {
          try { entry.payload = JSON.parse(params.payload); } catch (e) { entry.payload = params.payload; }
        } else entry[k] = ser(params[k]);
      });
      S.rec.urlFetch.push(entry);
      const spec = S.fetchQueue.length ? S.fetchQueue.shift() : (S.defaultFetch || defaultGeminiResponse());
      if (spec.throw) throw new Error(spec.throw);
      const text = typeof spec.body === 'string' ? spec.body : JSON.stringify(spec.body);
      if (spec.code >= 400 && !params.muteHttpExceptions) {
        throw new Error('Exception: Request failed for ' + url + ' returned code ' + spec.code + '. Truncated server response: ' + text.substring(0, 100));
      }
      return strict('HTTPResponse', {
        getResponseCode: function () { return spec.code; },
        getContentText: function () { return text; },
        getHeaders: function () { return { 'Content-Type': 'application/json' }; },
      });
    },
  });

  // ------------------------------------------------------------------ DriveApp
  G.DriveApp = strict('DriveApp', {
    Access: strict('DriveApp.Access', { ANYONE: 'ANYONE', ANYONE_WITH_LINK: 'ANYONE_WITH_LINK', DOMAIN: 'DOMAIN', DOMAIN_WITH_LINK: 'DOMAIN_WITH_LINK', PRIVATE: 'PRIVATE' }),
    Permission: strict('DriveApp.Permission', { VIEW: 'VIEW', EDIT: 'EDIT', COMMENT: 'COMMENT', OWNER: 'OWNER', ORGANIZER: 'ORGANIZER', NONE: 'NONE' }),
    getFolderById: function (id) {
      S.rec.drive.push({ getFolderById: String(id) });
      if (S.failingFolders[id]) throw new Error('Exception: No item with the given ID could be found. Or perhaps you do not have permission to access it.');
      const folder = strict('Folder', {
        getId: function () { return String(id); },
        getName: function () { return 'Tickets (' + id + ')'; },
        createFile: function (blob) {
          if (!blob || !blob[KIND] || blob[KIND] !== 'Blob') gap('Folder.createFile with non-Blob argument');
          S.fileCounter++;
          const fid = 'file-' + S.fileCounter;
          const bytes = blob.getBytes();
          S.rec.drive.push({ createFile: { folder: String(id), id: fid, name: blob.getName(), contentType: blob.getContentType(), size: bytes.length, fnv1a: fnv(bytes) } });
          const file = strict('File', {
            getId: function () { return fid; },
            getName: function () { return blob.getName(); },
            getUrl: function () { return 'https://drive.google.com/file/d/' + fid + '/view?usp=drivesdk'; },
            setSharing: function (access, permission) {
              S.rec.drive.push({ setSharing: { file: fid, access: access, permission: permission } });
              if (S.sharingFailFolders[id]) throw new Error('Exception: Sharing is restricted by the domain administrator (mock).');
              return file;
            },
          });
          return file;
        },
      });
      return folder;
    },
  });

  // ------------------------------------------------------------------ SpreadsheetApp
  function newSheetModel(name, maxRows, maxCols) {
    return { name: name, cells: [], maxRows: maxRows, maxCols: maxCols, frozenRows: 0, colFormats: {}, cellFormats: {}, styles: {}, proxy: null };
  }
  function rawGet(sh, r, c) { const row = sh.cells[r - 1]; if (!row) return ''; const v = row[c - 1]; return v === undefined ? '' : v; }
  function rawSet(sh, r, c, v) { while (sh.cells.length < r) sh.cells.push([]); const row = sh.cells[r - 1]; while (row.length < c) row.push(''); row[c - 1] = v; }
  function fmtOf(sh, r, c) { const k = r + ',' + c; return sh.cellFormats[k] !== undefined ? sh.cellFormats[k] : (sh.colFormats[c] !== undefined ? sh.colFormats[c] : null); }
  function normalizeIn(sh, r, c, v) {
    if (v === null || v === undefined) return '';
    if (isDate(v)) return new RealDate(v.getTime());
    const t = typeof v;
    if (t === 'number' || t === 'boolean') return fmtOf(sh, r, c) === '@' ? String(v) : v;
    if (t === 'string') return v;
    return gap('writing a cell value of type ' + (Array.isArray(v) ? 'array' : t) + ' (' + a1(r, c) + ')');
  }
  function writeCell(sh, r, c, v) { rawSet(sh, r, c, normalizeIn(sh, r, c, v)); }
  function readCell(sh, r, c) { const v = rawGet(sh, r, c); return isDate(v) ? new RealDate(v.getTime()) : v; }
  function lastRow(sh) { for (let r = sh.cells.length; r >= 1; r--) { const row = sh.cells[r - 1]; if (row && row.some(function (v) { return v !== ''; })) return r; } return 0; }
  function lastCol(sh) { let m = 0; sh.cells.forEach(function (row) { for (let c = row.length; c > m; c--) { if (row[c - 1] !== '') { m = c; break; } } }); return m; }
  function shiftKeys(map, fn) { const out = {}; Object.keys(map).forEach(function (k) { const p = k.split(',').map(Number); const n = fn(p[0], p[1]); if (n) out[n[0] + ',' + n[1]] = map[k]; }); return out; }

  function parseA1(sh, s) {
    const str = String(s).replace(/\$/g, '').trim();
    let m = /^([A-Za-z]+):([A-Za-z]+)$/.exec(str);
    if (m) { const c1 = colNumber(m[1]), c2 = colNumber(m[2]); return [1, Math.min(c1, c2), sh.maxRows, Math.abs(c2 - c1) + 1]; }
    m = /^([A-Za-z]+)(\d+)(?::([A-Za-z]+)(\d+))?$/.exec(str);
    if (m) {
      const r1 = +m[2], c1 = colNumber(m[1]);
      if (!m[3]) return [r1, c1, 1, 1];
      const r2 = +m[4], c2 = colNumber(m[3]);
      return [Math.min(r1, r2), Math.min(c1, c2), Math.abs(r2 - r1) + 1, Math.abs(c2 - c1) + 1];
    }
    return gap('Sheet.getRange A1 notation "' + s + '"');
  }

  function makeRange(sh, r, c, nr, nc) {
    [r, c, nr, nc].forEach(function (x) { if (typeof x !== 'number' || !Number.isInteger(x)) throw new Error('Exception: Invalid argument (getRange got ' + JSON.stringify(ser(x)) + ')'); });
    if (nr < 1) throw new Error('Exception: The number of rows in the range must be at least 1.');
    if (nc < 1) throw new Error('Exception: The number of columns in the range must be at least 1.');
    if (r < 1 || c < 1 || r + nr - 1 > sh.maxRows || c + nc - 1 > sh.maxCols) {
      throw new Error('Exception: The coordinates of the range are outside the dimensions of the sheet. (' + sh.name + ' ' + a1(r, c) + ' ' + nr + 'x' + nc + ', sheet is ' + sh.maxRows + 'x' + sh.maxCols + ')');
    }
    const cells = function (fn) { for (let i = 0; i < nr; i++) for (let j = 0; j < nc; j++) fn(r + i, c + j); };
    const style = function (prop, v) { cells(function (rr, cc) { const k = rr + ',' + cc; sh.styles[k] = Object.assign({}, sh.styles[k] || {}); sh.styles[k][prop] = v; }); return self; };
    const api = {
      getRow: function () { return r; },
      getColumn: function () { return c; },
      getNumRows: function () { return nr; },
      getNumColumns: function () { return nc; },
      getLastRow: function () { return r + nr - 1; },
      getLastColumn: function () { return c + nc - 1; },
      getSheet: function () { return sh.proxy; },
      getA1Notation: function () { return nr === 1 && nc === 1 ? a1(r, c) : a1(r, c) + ':' + a1(r + nr - 1, c + nc - 1); },
      getValues: function () { const out = []; for (let i = 0; i < nr; i++) { const row = []; for (let j = 0; j < nc; j++) row.push(readCell(sh, r + i, c + j)); out.push(row); } return out; },
      getValue: function () { return readCell(sh, r, c); },
      setValues: function (values) {
        if (!Array.isArray(values)) throw new Error('Exception: The parameters (' + typeof values + ') don\'t match the method signature for SpreadsheetApp.Range.setValues.');
        if (values.length !== nr) throw new Error('Exception: The number of rows in the data does not match the number of rows in the range. The data has ' + values.length + ' but the range has ' + nr + '.');
        values.forEach(function (row) {
          if (!Array.isArray(row) || row.length !== nc) throw new Error('Exception: The number of columns in the data does not match the number of columns in the range. The data has ' + (Array.isArray(row) ? row.length : 'non-array') + ' but the range has ' + nc + '.');
        });
        for (let i = 0; i < nr; i++) for (let j = 0; j < nc; j++) writeCell(sh, r + i, c + j, values[i][j]);
        return self;
      },
      setValue: function (v) { cells(function (rr, cc) { writeCell(sh, rr, cc, v); }); return self; },
      setNumberFormat: function (f) {
        if (r === 1 && nr === sh.maxRows) { // whole column(s): store as column format
          for (let j = 0; j < nc; j++) {
            const cc = c + j;
            sh.colFormats[cc] = String(f);
            Object.keys(sh.cellFormats).forEach(function (k) { if (Number(k.split(',')[1]) === cc) delete sh.cellFormats[k]; });
          }
        } else cells(function (rr, cc) { sh.cellFormats[rr + ',' + cc] = String(f); });
        return self;
      },
      setFontWeight: function (w) { return style('fontWeight', w); },
      setBackground: function (b) { return style('background', b); },
      setFontColor: function (b) { return style('fontColor', b); },
      setHorizontalAlignment: function (b) { return style('horizontalAlignment', b); },
    };
    const self = strict('Range', api);
    return self;
  }

  function makeSheet(sh) {
    const api = {
      getName: function () { return sh.name; },
      getSheetName: function () { return sh.name; },
      getIndex: function () { return S.ss.sheets.indexOf(sh) + 1; },
      getMaxRows: function () { return sh.maxRows; },
      getMaxColumns: function () { return sh.maxCols; },
      getLastRow: function () { return lastRow(sh); },
      getLastColumn: function () { return lastCol(sh); },
      getFrozenRows: function () { return sh.frozenRows; },
      setFrozenRows: function (n) { sh.frozenRows = n; S.rec.sheetOps.push({ sheet: sh.name, setFrozenRows: n }); },
      getRange: function (a, b, cnr, cnc) {
        if (typeof a === 'string' && b === undefined) { const p = parseA1(sh, a); return makeRange(sh, p[0], p[1], p[2], p[3]); }
        return makeRange(sh, a, b, cnr === undefined ? 1 : cnr, cnc === undefined ? 1 : cnc);
      },
      getDataRange: function () { return makeRange(sh, 1, 1, Math.max(lastRow(sh), 1), Math.max(lastCol(sh), 1)); },
      appendRow: function (values) {
        if (!Array.isArray(values) || values.length === 0) throw new Error('Exception: The parameters don\'t match the method signature for SpreadsheetApp.Sheet.appendRow.');
        const r = lastRow(sh) + 1;
        if (r > sh.maxRows) sh.maxRows = r;
        if (values.length > sh.maxCols) sh.maxCols = values.length;
        values.forEach(function (v, j) { writeCell(sh, r, j + 1, v); });
        S.rec.sheetOps.push({ sheet: sh.name, appendRow: r });
        return self;
      },
      deleteRow: function (r) {
        if (!Number.isInteger(r) || r < 1 || r > sh.maxRows) throw new Error('Exception: Those rows are out of bounds.');
        if (sh.maxRows - sh.frozenRows <= 1 && r > sh.frozenRows) throw new Error('Exception: Sorry, it is not possible to delete all non-frozen rows.');
        if (r <= sh.cells.length) sh.cells.splice(r - 1, 1);
        sh.maxRows--;
        const shift = function (rr, cc) { return rr === r ? null : [rr > r ? rr - 1 : rr, cc]; };
        sh.cellFormats = shiftKeys(sh.cellFormats, shift);
        sh.styles = shiftKeys(sh.styles, shift);
        S.rec.sheetOps.push({ sheet: sh.name, deleteRow: r });
        return self;
      },
      insertColumnsAfter: function (after, n) {
        if (!Number.isInteger(after) || after < 1 || after > sh.maxCols) throw new Error('Exception: Those columns are out of bounds.');
        if (!Number.isInteger(n) || n < 1) throw new Error('Exception: The number of columns to insert must be at least 1.');
        sh.cells.forEach(function (row) { if (row.length > after) { const blanks = []; for (let k = 0; k < n; k++) blanks.push(''); row.splice.apply(row, [after, 0].concat(blanks)); } });
        sh.maxCols += n;
        const shift = function (rr, cc) { return [rr, cc > after ? cc + n : cc]; };
        sh.cellFormats = shiftKeys(sh.cellFormats, shift);
        sh.styles = shiftKeys(sh.styles, shift);
        const cf = {}; Object.keys(sh.colFormats).forEach(function (k) { const cc = Number(k); cf[cc > after ? cc + n : cc] = sh.colFormats[k]; }); sh.colFormats = cf;
        S.rec.sheetOps.push({ sheet: sh.name, insertColumnsAfter: after, n: n });
        return self;
      },
    };
    const self = strict('Sheet', api);
    sh.proxy = self;
    return self;
  }

  function makeSpreadsheet() {
    const ss = { sheets: [] };
    const api = {
      getId: function () { return 'mock-spreadsheet'; },
      getName: function () { return 'Finanzas (mock)'; },
      getSpreadsheetTimeZone: function () { return DEFAULT_TZ; },
      getSheets: function () { return ss.sheets.map(function (s) { return s.proxy; }); },
      getSheetByName: function (name) { const s = ss.sheets.find(function (x) { return x.name === name; }); return s ? s.proxy : null; },
      insertSheet: function (name) {
        if (typeof name !== 'string') gap('Spreadsheet.insertSheet with non-string argument');
        if (ss.sheets.some(function (x) { return x.name === name; })) throw new Error('Exception: A sheet with the name "' + name + '" already exists. Please enter another name.');
        const sh = newSheetModel(name, 1000, 26);
        makeSheet(sh);
        ss.sheets.push(sh); // NOTE: real Sheets inserts after the active sheet; mock appends at the end
        S.rec.sheetOps.push({ insertSheet: name });
        return sh.proxy;
      },
    };
    ss.proxy = strict('Spreadsheet', api);
    return ss;
  }

  G.SpreadsheetApp = strict('SpreadsheetApp', {
    getActiveSpreadsheet: function () { return S.ss.proxy; },
    getActive: function () { return S.ss.proxy; },
    flush: function () {},
  });

  // ------------------------------------------------------------------ snapshot
  function snapshot() {
    const sheets = S.ss.sheets.map(function (sh) {
      const lr = lastRow(sh), lc = lastCol(sh);
      const values = [];
      for (let r = 1; r <= lr; r++) { const row = []; for (let c = 1; c <= lc; c++) row.push(ser(rawGet(sh, r, c))); values.push(row); }
      const cols = {}; Object.keys(sh.colFormats).map(Number).sort(function (a, b) { return a - b; }).forEach(function (c) { cols[colLetter(c)] = sh.colFormats[c]; });
      const sortKeys = function (map) {
        const out = {};
        Object.keys(map).map(function (k) { return k.split(',').map(Number); })
          .sort(function (a, b) { return a[0] - b[0] || a[1] - b[1]; })
          .forEach(function (p) { out[a1(p[0], p[1])] = ser(map[p[0] + ',' + p[1]]); });
        return out;
      };
      return { name: sh.name, maxRows: sh.maxRows, maxCols: sh.maxCols, frozenRows: sh.frozenRows, values: values, numberFormats: { columns: cols, cells: sortKeys(sh.cellFormats) }, styles: sortKeys(sh.styles) };
    });
    return {
      sheets: sheets,
      properties: ser(S.props),
      triggers: S.triggers.map(function (t) { return ser(t); }),
    };
  }

  // ------------------------------------------------------------------ control API for harness.js
  G.__mock = {
    init: function (seedJson) {
      const seed = JSON.parse(seedJson);
      S.defaultNowMs = new RealDate(seed.now).getTime();
      S.nowMs = S.defaultNowMs;
      S.props = Object.assign({}, seed.properties || {});
      (seed.failingFolders || []).forEach(function (f) { S.failingFolders[f] = true; });
      (seed.sharingFailFolders || []).forEach(function (f) { S.sharingFailFolders[f] = true; });
      S.triggers = (seed.triggers || []).map(function (t, i) { return Object.assign({ id: 'trigger-seed-' + (i + 1) }, t); });
      S.ss = makeSpreadsheet();
      (seed.sheets || []).forEach(function (spec) {
        const sh = newSheetModel(spec.name, spec.maxRows || 1000, spec.maxCols || 26);
        makeSheet(sh);
        (spec.rows || []).forEach(function (row, i) { row.forEach(function (v, j) { const rv = revive(v); rawSet(sh, i + 1, j + 1, rv === undefined || rv === null ? '' : rv); }); });
        sh.frozenRows = spec.frozenRows || 0;
        Object.keys(spec.colFormats || {}).forEach(function (L) { sh.colFormats[colNumber(L)] = spec.colFormats[L]; });
        S.ss.sheets.push(sh);
      });
    },
    beginStep: function (setupJson) {
      const setup = JSON.parse(setupJson || '{}');
      S.rec = newRec();
      S.rng = 123456789;
      S.uuidCounter = 0;
      S.nowMs = setup.now ? new RealDate(setup.now).getTime() : S.defaultNowMs;
      S.lockBusy = !!setup.lockBusy;
      S.fetchQueue = (setup.fetchQueue || []).slice();
      S.defaultFetch = setup.defaultFetch || null;
      if (setup.setProperties) Object.keys(setup.setProperties).forEach(function (k) { S.props[k] = setup.setProperties[k]; });
      if (setup.deleteProperties) setup.deleteProperties.forEach(function (k) { delete S.props[k]; });
    },
    invoke: function (name, argsJson) {
      const fn = G[name];
      if (typeof fn !== 'function') throw new Error('HARNESS: entry point not found in script: ' + name);
      const args = revive(JSON.parse(argsJson));
      const result = fn.apply(null, args);
      if (result && result[KIND] === 'TextOutput') {
        const text = result.getContent();
        const out = { kind: 'TextOutput', mimeType: result.getMimeType() };
        try { out.json = JSON.parse(text); } catch (e) { out.text = text; }
        return JSON.stringify(out);
      }
      return JSON.stringify({ kind: result === undefined ? 'undefined' : typeof result, value: ser(result) });
    },
    endStep: function () {
      const rec = S.rec;
      return JSON.stringify(Object.assign({}, rec, { state: snapshot() }));
    },
  };
})(globalThis);
