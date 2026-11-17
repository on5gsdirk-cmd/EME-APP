import { describe, expect, test } from 'vitest';
import { readExpected } from './expected.js';
import { addRecentLocator, mutualWindows, nextMutualWindows } from '../src/lib/mutual.js';
import { decode, usableWindows } from '../src/lib/horizon.js';
import { toLatLon } from '../src/lib/maidenhead.js';

describe('mutual moon window (same table as MutualWindowsTest.kt)', () => {
  test('as in the shared table', () => {
    const rows = readExpected('mutual_window');
    expect(rows.length).toBe(7);
    for (const r of rows) {
      const home = toLatLon(r.home);
      const dx = toLatLon(r.dx);
      const w = nextMutualWindows(decode(r.horizon), Number(r.margin), home.latDeg, home.lonDeg, dx.latDeg, dx.lonDeg, Date.parse(r.now_utc));
      expect(w.length, `${r.dx} ${r.now_utc} count`).toBe(Number(r.count));
      const f = w[0];
      expect(Math.abs(f.startMillis - Date.parse(r.first_start_utc))).toBeLessThanOrEqual(60_000);
      expect(Math.abs(f.endMillis - Date.parse(r.first_end_utc))).toBeLessThanOrEqual(60_000);
      expect(Math.abs(f.maxElDeg - Number(r.first_max_el))).toBeLessThanOrEqual(0.05);
      expect(Math.abs(f.maxAtMillis - Date.parse(r.first_max_at_utc))).toBeLessThanOrEqual(60_000);
      expect(f.partial).toBe(r.first_partial === 'true');
    }
  });

  test('with yourself it is your own clear window', () => {
    const p = toLatLon('JO21SC');
    const h = decode('1;5;B 60 110 25;P 180 200 20');
    const start = Date.parse('2026-11-20T00:00:00Z');
    const end = start + 3 * 86_400_000;
    const mutual = mutualWindows(h, 0, p.latDeg, p.lonDeg, p.latDeg, p.lonDeg, start, end).map((w) => [w.startMillis, w.endMillis]);
    const own = usableWindows(h, p.latDeg, p.lonDeg, start, end).map((w) => [w.startMillis, w.endMillis]);
    expect(mutual).toEqual(own);
  });
});

test('recent locators: newest first, no duplicates, at most five (as MutualWindowsTest.kt)', () => {
  expect(addRecentLocator([], 'fn32')).toEqual(['FN32']);
  expect(addRecentLocator(['FN32'], 'QF22')).toEqual(['QF22', 'FN32']);
  expect(addRecentLocator(['QF22', 'FN32'], 'fn32')).toEqual(['FN32', 'QF22']);
  expect(addRecentLocator(['B', 'C', 'D', 'E', 'F'], 'A')).toEqual(['A', 'B', 'C', 'D', 'E']);
});
