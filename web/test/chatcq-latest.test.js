import { describe, expect, test } from 'vitest';
import { recentCqs as hb9qRecent } from '../src/lib/hb9q.js';
import { recentCqs as n0ukRecent } from '../src/lib/n0uk.js';
import { readExpected } from './expected.js';

// Chat CQ (#124): each station once, its most recent CQ. Same table as ChatCqLatestTest.kt.
const rows = readExpected('chatcq_latest');
const cases = (chat) => {
  const groups = new Map();
  for (const r of rows.filter((r) => r.chat === chat)) groups.set(r.case, [...(groups.get(r.case) ?? []), r]);
  return [...groups.entries()];
};
const expected = (rs) =>
  rs
    .filter((r) => r.expect !== '')
    .sort((a, b) => b.time_utc.localeCompare(a.time_utc))
    .map((r) => `${r.callsign} ${r.time_utc} ${r.expect}`);

describe('Chat CQ shows each station once, its most recent CQ', () => {
  test.each(cases('hb9q'))('hb9q %s', (_, rs) => {
    const now = Date.parse('2026-11-08T09:10:00Z');
    const messages = rs
      .map((r, i) => ({
        id: i + 1, time: r.time_utc, fullTime: `Sun 8th of Nov - ${r.time_utc}`, callsign: r.callsign, name: 'Op', recipient: null,
        text: `***** CQ ${r.freq} 1st Q65-60C CFOM 1500 *****`, isCq: true, senderId: '1',
      }))
      .reverse();
    expect(hb9qRecent(messages, now).map((c) => `${c.callsign} ${c.time} shown`)).toEqual(expected(rs));
  });

  test.each(cases('n0uk'))('n0uk %s', (_, rs) => {
    const now = Date.parse('2026-11-10T16:20:00Z');
    const messages = rs
      .map((r) => ({
        utcMillis: Date.parse(`2026-11-10T${r.time_utc}:00Z`), date: '10Nov', time: r.time_utc,
        text: r.kind === 'stop' ? 'stop cq' : `CQ ${r.freq} 1st JT65B`,
        callsign: r.callsign, station: r.callsign, name: 'Op', locator: 'JO21', dateTime: `10Nov ${r.time_utc}`,
      }))
      .reverse();
    expect(n0ukRecent(messages, now, 60).map((c) => `${c.callsign} ${c.time} ${c.ended ? 'ended' : 'shown'}`)).toEqual(expected(rs));
  });
});
