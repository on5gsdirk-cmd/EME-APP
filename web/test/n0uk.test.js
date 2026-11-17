import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';
import { kindOf, parseCq, parseN0uk, recentCqs, recentWriters } from '../src/lib/n0uk.js';

const resource = (name) => readFileSync(new URL(`../../app/src/test/resources/${name}`, import.meta.url), 'utf8');
const millis = (iso) => Date.parse(iso);

describe('N0ukParser', () => {
  // Real N0UK page of 13 November 2026; e-mail addresses replaced by xxxx@test.com.
  const page = resource('n0uk_jt65emeA.html');
  const now = millis('2026-11-13T15:02:00Z');
  const messages = parseN0uk(page, now);

  const msg = (text, call = 'F4HBY', time = '16:11') => ({
    utcMillis: millis(`2026-11-10T${time}:00Z`),
    date: '10Nov',
    time,
    text,
    callsign: call,
    station: `${call}/2X15/700W`,
    name: 'Op',
    locator: 'IN97at',
    dateTime: `10Nov ${time}`,
  });

  test('parsesEveryLine', () => {
    expect(messages).toHaveLength(50);
    const first = messages[0];
    expect(first.date).toBe('13Nov');
    expect(first.time).toBe('12:03');
    expect(first.callsign).toBe('OM4CW');
    expect(first.station).toBe('OM4CW/1X18H/750');
    expect(first.name).toBe('Vlado');
    expect(first.locator).toBe('JN88un');
    expect(first.utcMillis).toBe(millis('2026-11-13T12:03:00Z'));
    expect(first.text.startsWith('DF2ZC Hi Bernd')).toBe(true);
  });

  test('stationWithoutSlashAndHtmlEntities', () => {
    const w0pr = messages.find((m) => m.callsign === 'W0PR');
    expect(w0pr.station).toBe('W0PR');
    expect(w0pr.name).toBe('Larry');
    expect(w0pr.locator).toBe('EN34hl');
    // "&amp;" in the page becomes "&"
    expect(messages.some((m) => m.text.includes('73 & CUL aloha'))).toBe(true);
  });

  test('noEmailAddressesInParsedData', () => {
    for (const m of messages) {
      expect(JSON.stringify(m)).not.toMatch(/[\w.]+@[\w-]+\.\w+/);
    }
  });

  test('cqVariantsAreRecognised', () => {
    const fields = (t) => {
      const c = parseCq(msg(t));
      return `${c.freq} ${c.period} ${c.mode}`;
    };
    expect(fields('============== CQ CQ .131 JT65B 1st=====')).toBe('144.131 1st JT65B');
    expect(fields('....CQ .124 JT65b 2nd')).toBe('144.124 2nd JT65B');
    expect(fields('     . CQ CQ CQ .130 1st Q65-60A --- CQ Q65-60A 1st .130 NH6V Hawaii')).toBe('144.130 1st Q65-60A');
    expect(fields('CQ 144.120')).toBe('144.120 ? ?');
    expect(fields('cq .11 2nd jt65c')).toBe('144.110 2nd JT65C');
  });

  test('decodesStopsAndChatAreNotCq', () => {
    expect(kindOf(msg('1544 -30  2.7 1586 #* CQ F4HBY IN97'))).toBe('OTHER'); // decode of someone else
    expect(kindOf(msg('1551 -24  2.8 1158 #* F4HBY KB9TVR EM49'))).toBe('OTHER');
    expect(kindOf(msg('=== stop  cq .131'))).toBe('STOP');
    expect(kindOf(msg('Stop CQ .130'))).toBe('STOP');
    expect(kindOf(msg("F4HBY Laurent I didn't know you were CQing"))).toBe('OTHER');
    expect(kindOf(msg('KB9TVR Robin Where is he transmitting?'))).toBe('OTHER');
    expect(parseCq(msg('=== stop  cq .131'))).toBeNull();
  });

  test('recentCqsFromRealPageWithStops', () => {
    // On 10 November at 16:20 UTC: window of 60 minutes
    const t = millis('2026-11-10T16:20:00Z');
    const cqs = recentCqs(parseN0uk(page, t), t, 60);
    // F4HBY called at 15:30 (stopped at 15:57) and at 16:11: only the most recent (#124)
    expect(cqs.map((c) => c.time)).toEqual(['16:11']);
    expect(cqs.every((c) => c.callsign === 'F4HBY' && c.freq === '144.131' && c.mode === 'JT65B' && c.period === '1st')).toBe(true);
    expect(cqs[0].ended).toBe(false); // 16:11 is still running
    expect(cqs[0].locator).toBe('IN97at');
  });

  test('duplicatePostIsShownOnce', () => {
    const t = millis('2026-11-10T13:50:00Z');
    const cqs = recentCqs(parseN0uk(page, t), t, 60);
    expect(cqs.filter((c) => c.time === '13:40')).toHaveLength(1); // posted twice at 13:40
  });

  test('recentWritersNewestFirstWithoutMe', () => {
    const w = recentWriters(messages, 'OM4CW');
    expect(w[0].callsign).toBe('WA4MYO');
    expect(w.length).toBe(new Set(w.map((m) => m.callsign)).size);
    expect(w.some((m) => m.callsign === 'OM4CW')).toBe(false);
    // The date shows WA4MYO last wrote on 11 November
    expect(w[0].dateTime).toBe('11Nov 15:44');
    expect(w.find((m) => m.callsign === 'F4HBY').dateTime).toBe('10Nov 16:28');
  });
});

describe('N0UK entity decoding', () => {
  const now = millis('2026-11-13T15:02:00Z');
  const textOf = (text) => {
    const page = `<pre>
13Nov 12:03 ${text} ====== {<a href="javascript:mailto('xxxx','test.com','')">DF2ZC/1X18H/750</a> Bernd xx JO62 }
</pre>`;
    return parseN0uk(page, now)[0].text;
  };

  test('numeric entity', () => expect(textOf('a&#65;b')).toBe('aAb'));
  test('hex entity, either case of x', () => {
    expect(textOf('a&#x42;b')).toBe('aBb');
    expect(textOf('a&#X43;b')).toBe('aCb');
  });
  test('latin1 named entity as in the title of the real page', () => expect(textOf('N&Oslash;UK')).toBe('NØUK'));
  test('named entity outside latin1', () => expect(textOf('a&euro;b')).toBe('a€b'));
  test('unknown name stays as written', () => expect(textOf('a&bogus;b')).toBe('a&bogus;b'));
  test('entity without semicolon stays as written', () => expect(textOf('a&amp b')).toBe('a&amp b'));
  test('invalid code points stay as written', () => {
    expect(textOf('a&#0;b')).toBe('a&#0;b');
    expect(textOf('a&#xD800;b')).toBe('a&#xD800;b');
  });
});
