import { describe, expect, test } from 'vitest';
import { MAX_STEPS, STEP_MS, lookAheadBase, lookAheadLabel, shownMillis } from '../src/lib/look-ahead.js';
import { readExpected } from './expected.js';

describe('look ahead (same table as LookAheadTest.kt)', () => {
  const rows = readExpected('look_ahead');
  test('table has 8 rows', () => expect(rows).toHaveLength(8));
  test.each(rows)('$now_utc + $steps steps', (r) => {
    const now = Date.parse(r.now_utc);
    const base = lookAheadBase(now);
    expect(base).toBe(Date.parse(r.base_utc));
    expect(shownMillis(now, base, Number(r.steps))).toBe(Date.parse(r.shown_utc));
    expect(lookAheadLabel(Number(r.steps))).toBe(r.label);
  });
  test('48 hours in steps of 10 minutes', () => expect(MAX_STEPS * STEP_MS).toBe(48 * 3_600_000));
});
