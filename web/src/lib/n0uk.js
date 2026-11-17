// Port of N0ukParser.kt: parser for the N0UK WSJT EME chat (www.chris.org/cgi-bin/jt65emeA, 144 MHz only).
// Messages are plain text in a <pre> block, one per line:
//   13Nov 12:03 message text ====== {<a href="javascript:mailto(...)">F4HBY/2X15/700W</a> Laurent xx IN97at }

const LINE = /^(\d{1,2})([A-Za-z]{3}) (\d{2}):(\d{2}) (.*) ====== \{<a [^>]*>([^<]*)<\/a>\s*(.*?)\s*\}\s*$/;
const GRID = /^[A-R]{2}\d{2}([A-X]{2})?$/i;
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

const LATIN1 = (
  'nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg plusmn sup2 sup3 acute micro ' +
  'para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil ' +
  'Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave ' +
  'Uacute Ucirc Uuml Yacute THORN szlig agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml ' +
  'igrave iacute icirc iuml eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute ' +
  'thorn yuml'
).split(' ');

const ENTITIES = {
  amp: '&', lt: '<', gt: '>', quot: '"', apos: "'",
  ndash: '–', mdash: '—', lsquo: '‘', rsquo: '’', ldquo: '“', rdquo: '”',
  hellip: '…', euro: '€', bull: '•',
};
LATIN1.forEach((name, i) => {
  ENTITIES[name] = String.fromCharCode(160 + i);
});

// Jsoup's Parser.unescapeEntities(s, false), reduced to numeric entities and the named ones above.
function unescapeEntities(s) {
  return s.replace(/&(?:#(\d+)|#[xX]([0-9a-fA-F]+)|([A-Za-z][A-Za-z0-9]*));/g, (whole, dec, hex, name) => {
    if (name !== undefined) return Object.hasOwn(ENTITIES, name) ? ENTITIES[name] : whole;
    const cp = dec !== undefined ? parseInt(dec, 10) : parseInt(hex, 16);
    return cp > 0 && cp <= 0x10ffff && !(cp >= 0xd800 && cp <= 0xdfff) ? String.fromCodePoint(cp) : whole;
  });
}

// Jsoup.parse(s).text().trim(): drop tags, decode entities, collapse whitespace.
function htmlText(s) {
  return unescapeEntities(s.replace(/<\/?[A-Za-z!][^>]*>/g, '')).replace(/\s+/g, ' ').trim();
}

const daysInMonth = (year, month) => new Date(Date.UTC(year, month, 0)).getUTCDate();

/** All messages of the page, in page order (newest first). */
export function parseN0uk(html, nowMs) {
  const pre = /<pre>(.*?)<\/pre>/is.exec(html);
  if (!pre) return [];
  const now = new Date(nowMs);
  const year = now.getUTCFullYear();
  const todayPlus1 = Date.UTC(year, now.getUTCMonth(), now.getUTCDate() + 1);
  const out = [];
  for (const raw of pre[1].split(/\r\n|\r|\n/)) {
    const m = LINE.exec(raw.trim());
    if (!m) continue;
    const [, day, mon, hh, mm, text, station, rest] = m;
    const month = MONTHS.indexOf(mon.toLowerCase()) + 1;
    if (month === 0) continue;
    const dayNr = parseInt(day, 10);
    if (dayNr < 1 || dayNr > daysInMonth(year, month)) continue;
    let dateYear = year;
    let dateDay = dayNr;
    if (Date.UTC(year, month - 1, dayNr) > todayPlus1) { // year change
      dateYear -= 1;
      dateDay = Math.min(dayNr, daysInMonth(dateYear, month)); // LocalDate.minusYears clamps 29 Feb
    }
    const hours = parseInt(hh, 10);
    const minutes = parseInt(mm, 10);
    if (hours > 23 || minutes > 59) throw new RangeError(`Invalid time ${hh}:${mm}`); // Kotlin: DateTimeException from atTime
    const utcMillis = Date.UTC(dateYear, month - 1, dateDay, hours, minutes);
    const parts = unescapeEntities(rest).trim().split(/[ \t\n\x0B\f\r]+/).filter((p) => p.length > 0);
    const last = parts.length ? parts[parts.length - 1] : undefined;
    const locator = last !== undefined && GRID.test(last) ? last : '';
    const nameParts = locator ? parts.slice(0, -1) : parts;
    // The second to last field is the state/province ("xx" when missing)
    const name = (nameParts.length >= 2 ? nameParts.slice(0, -1) : nameParts).join(' ');
    const stationText = unescapeEntities(station).trim();
    out.push({
      utcMillis,
      date: `${day}${mon}`,
      time: `${hh}:${mm}`,
      text: htmlText(text),
      callsign: stationText.split('/')[0].toUpperCase(),
      station: stationText,
      name,
      locator,
      dateTime: `${day}${mon} ${hh}:${mm}`,
    });
  }
  return out;
}

// CQ calls are not standardised, e.g. "============== CQ CQ .131 JT65B 1st=====", "....CQ .124 JT65b 2nd".
// ".131" means 144.131 MHz. A posted decode of someone else ("1544 -30 2.7 1586 #* CQ F4HBY IN97") is not a CQ
// of the sender. "stop cq .131" ends a CQ.
const CQ = /\bcq\b/i;
const STOP = /\bstop\b/i;
const DECODE = /^\s*\d{4}\s+-?\d+\s+-?\d+(\.\d+)?\s+-?\d+\s/;
const FREQ = /(?<![\d.])(?:144)?\.(\d{2,3})(?!\d)/;
const PERIOD = /\b(1st|2nd)\b/i;
const MODE = /\b(Q65-?\d{2,3}[A-E]|JT65[A-C]?|JT4[A-G]?|FST4|FT8|MSK144|CW)\b/i;

const WINDOW_SLACK_MS = 120_000;

/** 'CQ' | 'STOP' | 'OTHER' */
export function kindOf(msg) {
  const t = msg.text;
  if (!CQ.test(t) || DECODE.test(t)) return 'OTHER';
  if (STOP.test(t)) return 'STOP';
  // Without frequency and without mode it is not a usable CQ call
  return FREQ.test(t) || MODE.test(t) ? 'CQ' : 'OTHER';
}

export function parseCq(msg) {
  if (kindOf(msg) !== 'CQ') return null;
  const t = msg.text;
  const f = FREQ.exec(t);
  const freq = f ? `144.${f[1].padEnd(3, '0')}` : '?';
  const mm = MODE.exec(t);
  let mode = '?';
  if (mm) {
    mode = mm[0].toUpperCase();
    // "Q6560A" -> "Q65-60A"
    if (mode.startsWith('Q65') && !mode.startsWith('Q65-')) mode = `Q65-${mode.slice(3)}`;
  }
  const p = PERIOD.exec(t);
  return {
    utcMillis: msg.utcMillis,
    time: msg.time,
    freq,
    period: p ? p[0].toLowerCase() : '?',
    callsign: msg.callsign,
    mode,
    locator: msg.locator,
    ended: false,
  };
}

/**
 * CQs of the last `minutes` minutes, newest first. Each station once: its most recent CQ (#124), case does
 * not matter. When the station wrote "stop cq" after it, that CQ gets ended = true.
 */
export function recentCqs(messages, nowMs, minutes) {
  // Only "stop cq" up to now counts (a stop "from the future" ends nothing)
  const stops = messages.filter((m) => kindOf(m) === 'STOP' && m.utcMillis <= nowMs + WINDOW_SLACK_MS);
  const cqs = [];
  for (const m of messages) {
    const cq = parseCq(m);
    if (!cq) continue;
    const age = nowMs - cq.utcMillis;
    if (age < -WINDOW_SLACK_MS || age > minutes * 60_000) continue;
    cqs.push(cq);
  }
  // Stable sort: of two CQs in the same minute, the first in the list (as the app's sortedByDescending) wins
  cqs.sort((a, b) => b.utcMillis - a.utcMillis);
  const seen = new Set();
  return cqs.filter((cq) => {
    const key = cq.callsign.toUpperCase();
    if (seen.has(key)) return false;
    seen.add(key);
    cq.ended = stops.some((s) => s.callsign.toUpperCase() === key && s.utcMillis >= cq.utcMillis);
    return true;
  });
}

/** Who wrote in the chat recently (except yourself), with the time of their latest message. */
export function recentWriters(messages, myCallsign) {
  const me = myCallsign.toUpperCase();
  const seen = new Set();
  return messages.filter((m) => {
    const call = m.callsign.toUpperCase();
    if (call === me || seen.has(call)) return false;
    seen.add(call);
    return true;
  });
}
