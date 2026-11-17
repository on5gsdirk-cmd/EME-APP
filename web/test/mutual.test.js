import { describe, expect, test } from 'vitest';
import { readExpected } from './expected.js';
import { addRecentLocator, mutualWindows, nextMutualWindows } from '../src/lib/mutual.js';
import { VISIBILITY, decode, usableWindows, visibility } from '../src/lib/horizon.js';
import { position } from '../src/lib/moon-calculator.js';
import { toLatLon } from '../src/lib/maidenhead.js';

// 8 rows of 30 days and more, minute by minute: 3-4 s per test alone, over 7 s in a full parallel run.
const SLOW_MS = 30_000;

describe('mutual moon window (same table as MutualWindowsTest.kt)', () => {
  test('as in the shared table', () => {
    const rows = readExpected('mutual_window');
    expect(rows.length).toBe(8);
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
      const l = w.at(-1);
      expect(Math.abs(l.startMillis - Date.parse(r.last_start_utc))).toBeLessThanOrEqual(60_000);
      expect(Math.abs(l.endMillis - Date.parse(r.last_end_utc))).toBeLessThanOrEqual(60_000);
    }
  }, SLOW_MS);

  // The edges are real (#120): the minute before the first window and the minute the last one ends are not mutual,
  // also for a window longer than a day or one running past the 30 days. Computed without mutual.js.
  test('the first start and the last end are real edges', () => {
    for (const r of readExpected('mutual_window')) {
      const horizon = decode(r.horizon);
      const margin = Number(r.margin);
      const home = toLatLon(r.home);
      const dx = toLatLon(r.dx);
      const ok = (t) => {
        const h = position(home.latDeg, home.lonDeg, t);
        return visibility(horizon, h.azimuthDeg, h.elevationDeg, margin) !== VISIBILITY.BLOCKED && position(dx.latDeg, dx.lonDeg, t).elevationDeg > 0;
      };
      const w = nextMutualWindows(horizon, margin, home.latDeg, home.lonDeg, dx.latDeg, dx.lonDeg, Date.parse(r.now_utc));
      expect(ok(w[0].startMillis) && !ok(w[0].startMillis - 60_000), `${r.dx} ${r.now_utc} first start`).toBe(true);
      expect(ok(w.at(-1).endMillis - 60_000) && !ok(w.at(-1).endMillis), `${r.dx} ${r.now_utc} last end`).toBe(true);
    }
  }, SLOW_MS);

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
