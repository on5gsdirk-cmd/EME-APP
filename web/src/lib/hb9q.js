// Port of the pure HB9Q parsers: ChatParser.parseRows, ChatCqParser, ChatStore.recentParticipants,
// Hb9qClient.loggerRows (plus the two checks of postJson) and the Hb9qBand enum of ChatMessage.kt.
// parseRows needs DOMParser (browser, or jsdom in tests). DOMParser runs no scripts.

/** The band buttons of the HB9Q chat; id is the number the HB9Q site uses. Order of Hb9qBand. */
export const HB9Q_BANDS = [
  { name: 'B50', id: 1, label: '50' },
  { name: 'B144', id: 2, label: '144' },
  { name: 'B222', id: 3, label: '222' },
  { name: 'B432', id: 4, label: '432' },
  { name: 'B902', id: 5, label: '902' },
  { name: 'B1296', id: 6, label: '1296' },
  { name: 'B23XX', id: 7, label: '23xx' },
  { name: 'B3400', id: 10, label: '3400' },
  { name: 'B5760', id: 9, label: '5760' },
  { name: 'B10G', id: 8, label: '10xxx' },
  { name: 'B24G', id: 11, label: '24048' },
  { name: 'B47G', id: 12, label: '47088' },
  { name: 'B76G', id: 13, label: '76032' },
];

export const bandByName = (name) => HB9Q_BANDS.find((b) => b.name === name);

// ---------- ChatParser.parseRows ----------

const TIME_IN_ONCLICK = /showTime\(this,'([^']*)'\)/;

// Jsoup text(): the text with runs of whitespace collapsed to one space.
const text = (node) => node.textContent.replace(/\s+/g, ' ').trim();

export function parseRows(html) {
  const doc = new DOMParser().parseFromString(`<table><tbody>${html}</tbody></table>`, 'text/html');
  const messages = [];
  for (const tr of doc.querySelectorAll('tr[id^="msg_"]')) {
    const tds = Array.from(tr.children).filter((c) => c.tagName === 'TD');
    if (tds.length < 4) continue;
    const idText = tr.id.slice('msg_'.length);
    if (!/^[+-]?\d+$/.test(idText)) continue;

    const textCell = tds[1].cloneNode(true);
    const recipientSpan = Array.from(textCell.children).find((c) => c.tagName === 'SPAN');
    let recipient = null;
    if (recipientSpan) {
      const t = text(recipientSpan);
      if (t.startsWith('@')) {
        recipient = t.slice(1).replace(/:$/, '').trim();
        recipientSpan.remove();
      }
    }

    const nameSpan = tds[4]?.querySelector('span');
    const senderClass = Array.from(tr.classList).find((c) => c.startsWith('msg_mem_'));
    messages.push({
      id: Number(idText),
      time: text(tds[0]),
      fullTime: TIME_IN_ONCLICK.exec(tds[0].getAttribute('onclick') ?? '')?.[1] ?? '',
      callsign: text(tds[3]),
      name: nameSpan ? text(nameSpan) : '',
      recipient,
      text: text(textCell),
      isCq: tr.classList.contains('msgtype_10'),
      senderId: senderClass ? senderClass.slice('msg_mem_'.length) : null,
    });
  }
  return messages;
}

// ---------- Hb9qClient.loggerRows + postJson checks ----------

/**
 * The "logger" value of a successful response. A missing field is a broken response, not an
 * empty chat; a present but empty field is an empty chat.
 */
export function loggerRows(jsonText) {
  let json;
  try {
    json = JSON.parse(jsonText);
  } catch {
    json = null;
  }
  if (json === null || typeof json !== 'object' || Array.isArray(json)) throw new Error('HB9Q: unexpected answer');
  if (json.status !== 'success') throw new Error('HB9Q: data not available');
  if (!('logger' in json)) throw new Error("HB9Q: antwoord zonder 'logger'-veld");
  return json.logger == null ? '' : String(json.logger);
}

// ---------- ChatStore.recentParticipants ----------

/** Stations that wrote recently (except yourself), newest first; messages are newest first. */
export function recentParticipants(messages, myCallsign) {
  const mine = myCallsign.toUpperCase();
  const seen = new Set();
  const result = [];
  for (const m of messages) {
    const key = m.callsign.toUpperCase();
    if (key === mine || seen.has(key)) continue;
    seen.add(key);
    result.push({ time: m.time, callsign: m.callsign, name: m.name });
  }
  return result;
}

// ---------- ChatCqParser ----------

export const CHAT_CQ_WINDOW_MINUTES = 60;

const FREQ = /\b(\d{2,5})\.(\d{0,3})/;
const PERIOD = /\b(1st|2nd)\b/i;
const Q65 = /\bQ65[A-E]?\s*-?\s*(\d{2,3})\s*([A-E])\b/i;
const OTHER_MODE = /\b(JT65[A-C]?|JT4[A-G]?|JT9|QRA64[A-E]?|FT8|FT4|FST4|MSK144|JTTY|ISCAT-?[AB]?|CW|SSB)\b/i;
const CFOM = /CFOM/i;
const OWN_ECHO = /own\s*echo/i;
// Hz (#12): a number of 3-4 digits right after the mode or CFOM ("Q65-60C 1500", "CFOM @1500") that is not a
// power (W), distance (m), duration (min) or dB; or right before "hz" ("1500Hz"). A loose number (the frequency
// without a dot, a clock time) is not Hz: "?" rather than a wrong number. Same as ChatCqParser.kt.
const HZ_AFTER = new RegExp(
  `(?:${Q65.source}|${OTHER_MODE.source}|CFOM)` + String.raw`[\s,;:@/-]*(\d{3,4})(?![\d.])(?!\s*(?:w\b|m\b|min|db|watt))`,
  'gi',
);
const HZ_BEFORE = /(?<![\d.])(\d{3,4})\s*hz\b/gi;
const CQ_WORD = /\bCQ\b/i;
const CLOCK = /(\d{1,2}):(\d{2})/;
const DAY_OF_MONTH = /(\d{1,2})(?:st|nd|rd|th)\s+of\s+([A-Za-z]{3})/;
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

// Cyrillic letters that look like Latin ones.
const LOOKALIKES = {
  'А': 'A', 'В': 'B', 'С': 'C', 'Е': 'E', 'Н': 'H', 'К': 'K', 'М': 'M',
  'О': 'O', 'Р': 'P', 'Т': 'T', 'Х': 'X',
  'а': 'a', 'с': 'c', 'е': 'e', 'о': 'o', 'р': 'p', 'х': 'x',
};

export const isCq = (msg) => msg.isCq || (msg.text.includes('*****') && CQ_WORD.test(msg.text));

/** Clean the text: replace lookalikes, drop asterisks and >> <<, normalise spaces. */
export const clean = (s) =>
  Array.from(s, (c) => LOOKALIKES[c] ?? c)
    .join('')
    .replace(/[*<>]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

const startOfUtcDay = (ms) => Math.floor(ms / 86_400_000) * 86_400_000;

/**
 * Time of a chat message in UTC. Shows "07:16" and (in fullTime) "Fri 6th of Nov - 07:16".
 * Without a usable date: the most recent moment with that clock time (today or yesterday).
 * null when msg.time is not in "H:MM" format.
 */
export function messageMillis(msg, nowMs) {
  const hm = CLOCK.exec(msg.time);
  if (!hm) return null;
  const timeOfDay = (Number(hm[1]) * 60 + Number(hm[2])) * 60_000;
  const now = new Date(nowMs);
  const today = startOfUtcDay(nowMs);

  let day = null;
  const dm = DAY_OF_MONTH.exec(msg.fullTime);
  if (dm) {
    const month = MONTHS.indexOf(dm[2].toLowerCase());
    if (month >= 0) {
      const daysInMonth = (year) => new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
      const at = (year) => Date.UTC(year, month, Math.min(Math.max(Number(dm[1]), 1), daysInMonth(year)));
      day = at(now.getUTCFullYear());
      if (day > today + 86_400_000) day = at(now.getUTCFullYear() - 1); // year change
    }
  }
  if (day === null) day = today + timeOfDay > nowMs + 2 * 60_000 ? today - 86_400_000 : today;
  return day + timeOfDay;
}

export function parseCq(msg, nowMs) {
  if (!isCq(msg)) return null;
  // No usable time: leave this CQ out rather than guess (epoch 0 would silently drop as "too old").
  const millis = messageMillis(msg, nowMs);
  if (millis === null) return null;
  const t = clean(msg.text);

  const f = FREQ.exec(t);
  const freq = f ? (f[2] === '' ? `${f[1]}.?` : f[0]) : '?';
  const rest = f ? t.slice(0, f.index) + t.slice(f.index + f[0].length) : t;

  const period = PERIOD.exec(rest)?.[0].toLowerCase() ?? '?';
  const q65 = Q65.exec(rest);
  let mode;
  if (q65) {
    mode = `Q65-${q65[1]}${q65[2].toUpperCase()}`;
  } else {
    const other = OTHER_MODE.exec(rest)?.[0].toUpperCase();
    mode = other === undefined ? '?' : other === 'ISCATA' || other === 'ISCATB' ? `ISCAT-${other.slice(-1)}` : other;
  }
  const cfom = CFOM.test(rest) ? 'CFOM' : OWN_ECHO.test(rest) ? 'own echo' : '?';
  // Hz: the first number between 200 and 3000 at a place where Hz is written (after the mode or CFOM, or before "hz")
  const hzValue = [...rest.matchAll(HZ_AFTER), ...rest.matchAll(HZ_BEFORE)]
    .map((m) => {
      const n = m[m.length - 1];
      return { at: m.index + m[0].lastIndexOf(n), n: Number(n) };
    })
    .sort((a, b) => a.at - b.at)
    .map((c) => c.n)
    .find((n) => n >= 200 && n <= 3000);

  return {
    messageId: msg.id,
    utcMillis: millis,
    time: msg.time,
    freq,
    period,
    callsign: msg.callsign,
    name: msg.name.trim() === '' ? '?' : msg.name,
    mode,
    cfom,
    hz: hzValue === undefined ? '?' : String(hzValue),
  };
}

/** CQs of the last [minutes] minutes, newest first; each station once: its most recent CQ (#124), case does not matter. */
export function recentCqs(messages, nowMs, minutes = CHAT_CQ_WINDOW_MINUTES) {
  const seen = new Set();
  return messages
    .map((m) => parseCq(m, nowMs))
    .filter((c) => c !== null)
    .filter((c) => nowMs - c.utcMillis >= -120_000 && nowMs - c.utcMillis <= minutes * 60_000)
    .sort((a, b) => b.utcMillis - a.utcMillis || b.messageId - a.messageId)
    .filter((c) => {
      const key = c.callsign.toUpperCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}
