// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { createN0ukScreen } from '../src/screens/n0uk.js';
import { createN0ukStore } from '../src/data.js';
import { parseN0uk, recentCqs, recentWriters } from '../src/lib/n0uk.js';
import { quickElevationDeg, subLunarPoint } from '../src/lib/moon-calculator.js';
import { stationLatLon } from '../src/lib/livecq.js';
import { setLanguage, t } from '../src/i18n.js';
import { callsTo, fails, ok, resource, status, stubFetch } from './fetch-stub.js';

const N0UK = '/api/n0uk';
const NOW = Date.parse('2026-11-10T16:20:00Z'); // two CQs of F4HBY in the last hour (the older one stopped): only the newest shows
const page = resource('n0uk_jt65emeA.html');

let host;
let screen;
let store;
const mount = async (callsign = 'ON5GS') => {
  screen.mount(host, { settings: { callsign, locator: 'JO21SC', language: 'en' }, t, now: () => Date.now(), n0uk: store });
  await vi.advanceTimersByTimeAsync(0);
};
const q = (role) => host.querySelector(`[data-role="${role}"]`);
const subtab = (i) => host.querySelectorAll('[role="tab"]')[i];

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  setLanguage('en');
  stubFetch({ [N0UK]: ok(page, { 'X-Fetched-At': String(NOW - 30_000) }) });
  store = createN0ukStore();
  host = document.createElement('div');
  document.body.append(host);
  screen = createN0ukScreen();
});
afterEach(() => {
  screen.unmount();
  host.remove();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('N0UK screen: Messages', () => {
  test('renders every message of the sample with date, time, callsign, name and locator', async () => {
    await mount();
    const messages = parseN0uk(page, NOW);
    const rows = host.querySelectorAll('[data-role="message"]');
    expect(rows).toHaveLength(messages.length);
    expect(rows[0].textContent).toContain(`${messages[0].date} ${messages[0].time}`);
    expect(rows[0].textContent).toContain(messages[0].callsign);
    expect(rows[0].textContent).toContain(`${messages[0].name} ${messages[0].locator}`.trim());
    expect(rows[0].textContent).toContain(messages[0].text);
  });

  test('shows the info line, the update time from X-Fetched-At and the sub-tab labels', async () => {
    await mount();
    expect(host.textContent).toContain(t('n0uk_info'));
    expect(q('updated').textContent).toBe(t('updated', '16:19'));
    const writers = recentWriters(parseN0uk(page, NOW), 'ON5GS');
    expect([0, 1, 2].map((i) => subtab(i).textContent)).toEqual([t('messages'), t('participants', writers.length), t('tab_chatcq')]);
  });

  test('CQ messages are marked cq, stop messages stop, my own messages mine', async () => {
    await mount('F4HBY');
    const rows = [...host.querySelectorAll('[data-role="message"]')];
    const byText = (s) => rows.find((r) => r.textContent.includes(s));
    expect(byText('CQ CQ .131').querySelector('.text').classList.contains('cq')).toBe(true);
    expect(byText('stop cq .131').querySelector('.text').classList.contains('stop')).toBe(true);
    expect(byText('stop cq .131').querySelector('.call').classList.contains('mine')).toBe(true);
    expect(byText("didn't know").querySelector('.text').className).toBe('text');
  });

  test('message text is inserted as text, never as markup', async () => {
    const evil = page.replace('13Nov 12:03 DF2ZC Hi Bernd', '13Nov 12:03 &lt;img src=x onerror=alert(1)&gt;');
    stubFetch({ [N0UK]: ok(evil) });
    await mount();
    expect(host.querySelector('img')).toBeNull();
    expect(host.textContent).toContain('<img src=x onerror=alert(1)>');
  });
});

describe('N0UK screen: QRV', () => {
  test('lists who wrote recently, with date and time, without me', async () => {
    await mount('F4HBY');
    subtab(1).click();
    const writers = recentWriters(parseN0uk(page, NOW), 'F4HBY');
    expect(host.textContent).toContain(t('recent_in_chat'));
    const rows = [...host.querySelectorAll('[data-role="writer"]')];
    expect(rows.map((r) => r.querySelector('.call').textContent)).toEqual(writers.map((w) => w.callsign));
    expect(rows[0].textContent).toContain(writers[0].dateTime);
    expect(rows[0].textContent).toContain(writers[0].locator);
    expect(host.querySelector('[data-role="message"]')).toBeNull();
  });
});

describe('N0UK screen: Chat CQ', () => {
  test('lists the CQs of the last 60 minutes, one per station: its most recent', async () => {
    await mount();
    subtab(2).click();
    const cqs = recentCqs(parseN0uk(page, NOW), NOW, 60);
    expect(cqs.map((c) => c.time)).toEqual(['16:11']);
    expect(host.textContent).toContain(t('n0uk_cq_title', 1));
    const rows = [...host.querySelectorAll('[data-role="cq"]')];
    expect(rows).toHaveLength(1);
    expect(rows[0].classList.contains('ended')).toBe(false);
    const cells = [...rows[0].children].map((c) => c.textContent);
    expect(cells.slice(0, 6)).toEqual(['16:11', '144.131', '1st', 'F4HBY', 'JT65B', 'IN97at']);
    const sub = subLunarPoint(NOW);
    const p = stationLatLon('IN97at');
    const el = quickElevationDeg(sub, p.latDeg, p.lonDeg);
    expect(cells[6]).toBe(el > 0 ? `↑${el.toFixed(0)}°` : '↓');
    expect(host.querySelector('[data-role="cq-header"]').textContent).toBe(t('n0uk_cq_header').split(' - ').join(''));
  });

  test('a CQ stopped with "stop cq" is greyed out (16:05: F4HBY called at 15:30 and stopped at 15:57)', async () => {
    vi.setSystemTime(Date.parse('2026-11-10T16:05:00Z'));
    await mount();
    subtab(2).click();
    const rows = [...host.querySelectorAll('[data-role="cq"]')];
    expect(rows.map((r) => r.children[0].textContent)).toEqual(['15:30']);
    expect(rows[0].classList.contains('ended')).toBe(true);
  });

  test('without a locator the moon cell is "?", never NaN', async () => {
    const noLoc = page.replace(/(CQ CQ \.131 JT65B 1st=====.*?Laurent xx )IN97at/, '$1');
    stubFetch({ [N0UK]: ok(noLoc) });
    await mount();
    subtab(2).click();
    const row = host.querySelector('[data-role="cq"]');
    expect([...row.children].slice(5).map((c) => c.textContent)).toEqual(['?', '?']);
    expect(host.textContent).not.toMatch(/NaN/);
  });

  test('empty list shows n0uk_cq_empty', async () => {
    vi.setSystemTime(Date.parse('2026-11-13T15:02:00Z'));
    await mount();
    subtab(2).click();
    expect(host.textContent).toContain(t('n0uk_cq_empty'));
  });
});

describe('N0UK screen: errors and polling', () => {
  test('a network error keeps the messages and shows n0uk_error with the message', async () => {
    const f = stubFetch({ [N0UK]: ok(page) });
    await mount();
    f.handlers[N0UK] = fails('Failed to fetch');
    await vi.advanceTimersByTimeAsync(95_000);
    expect(q('error').textContent).toBe(t('n0uk_error', 'Failed to fetch'));
    expect(host.querySelectorAll('[data-role="message"]')).toHaveLength(50);
  });

  test('502 without a copy shows the upstream error and an empty list', async () => {
    stubFetch({ [N0UK]: status(502, 'N0UK HTTP 500', { 'X-Upstream-Error': 'N0UK HTTP 500' }) });
    await mount();
    expect(q('error').textContent).toBe(t('n0uk_error', 'N0UK HTTP 500'));
    expect(host.querySelectorAll('[data-role="message"]')).toHaveLength(0);
  });

  test('requests every 90 s while mounted, none after unmount', async () => {
    const f = stubFetch({ [N0UK]: ok(page) });
    await mount();
    await vi.advanceTimersByTimeAsync(185_000);
    expect(callsTo(f, N0UK)).toBe(3);
    screen.unmount();
    await vi.advanceTimersByTimeAsync(600_000);
    expect(callsTo(f, N0UK)).toBe(3);
  });
});
