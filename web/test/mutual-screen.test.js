// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, test } from 'vitest';
import { createMutualPanel } from '../src/screens/mutual.js';
import { setLanguage, t } from '../src/i18n.js';
import { decode, defaultHorizon } from '../src/lib/horizon.js';
import { withTimeZone } from './tz.js';

// Same as MutualWindowScreenTest.kt: JO21SC, 20 Nov 2026 12:00 UTC, Brussels time
const NOW = Date.parse('2026-11-20T12:00:00Z');

describe('"Mutual window" panel', () => {
  let panel;
  let settings;
  let restore;
  const q = (r) => panel.querySelector(`[data-role="${r}"]`);
  const all = (r) => [...panel.querySelectorAll(`[data-role="${r}"]`)];
  const type = (v) => {
    q('mutual-locator').value = v;
    q('mutual-locator').dispatchEvent(new Event('input'));
  };
  const tick = () => new Promise((r) => setTimeout(r, 0));

  beforeEach(() => {
    restore = withTimeZone('Europe/Brussels');
    localStorage.clear();
    setLanguage('en');
    settings = { locator: 'JO21SC', language: 'en', horizon: defaultHorizon(), degBand: 'B1296', dishDiameterM: 0, dishBand: 'B1296', mutualLocators: [] };
    panel = createMutualPanel({ settings, t, now: () => NOW, lang: 'en' }, () => {});
  });
  afterEach(() => restore());

  test('FN32 gives the 30-day table and is remembered', async () => {
    type('fn32');
    q('mutual-calculate').click();
    expect(q('mutual-status').textContent).toBe('Calculating…');
    await tick();
    const rows = all('mutual-row');
    expect(rows.length).toBe(29);
    expect(rows[0].textContent).toContain('19:58 – 03:49 (+1)   (7:51)');
    expect(rows[0].textContent).toContain('Max. EL 36.5°');
    expect(rows[0].textContent).toContain('(1296 MHz)');
    expect(q('mutual-pair').textContent).toBe('JO21SC  ↔  FN32');
    expect(JSON.parse(localStorage.getItem('eme.settings')).mutualLocators).toEqual(['FN32']);
  });

  test('a wrong locator cannot be calculated', () => {
    type('XX9');
    expect(q('mutual-calculate').disabled).toBe(true);
    type('QF22');
    expect(q('mutual-calculate').disabled).toBe(false);
  });

  test('a recent locator is one tap', async () => {
    type('QF22');
    q('mutual-calculate').click();
    await tick();
    type('FN32');
    q('mutual-calculate').click();
    await tick();
    expect(settings.mutualLocators).toEqual(['FN32', 'QF22']);
    all('mutual-recent').find((b) => b.textContent === 'QF22').click();
    await tick();
    expect(q('mutual-pair').textContent).toBe('JO21SC  ↔  QF22');
    expect(settings.mutualLocators).toEqual(['QF22', 'FN32']);
  });

  test('no mutual window with an obstacle all round', async () => {
    settings.horizon = decode('1;0;B 0 360 90');
    type('FN32');
    q('mutual-calculate').click();
    await tick();
    expect(q('mutual-none').textContent).toBe('No mutual window in the next 30 days.');
  });
});
