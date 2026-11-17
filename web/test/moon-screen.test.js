// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { createMoonScreen } from '../src/screens/moon.js';
import { setLanguage, t } from '../src/i18n.js';
import { decode } from '../src/lib/horizon.js';
import { withTimeZone } from './tz.js';
import { position } from '../src/lib/moon-calculator.js';
import { toLatLon } from '../src/lib/maidenhead.js';

// Wraps drawWorldMap to see what the map gets (time and spots); it still draws as before
const mapCalls = vi.hoisted(() => []);
vi.mock('../src/lib/world-map.js', async (importOriginal) => {
  const real = await importOriginal();
  return {
    ...real,
    drawWorldMap: (canvas, opts) => {
      mapCalls.push(opts);
      return real.drawWorldMap(canvas, opts);
    },
  };
});

const NOW = Date.parse('2026-11-07T10:00:00Z');
let host;
let screen;
let getContext;
let drawCalls;
let width;
const clientWidth = Object.getOwnPropertyDescriptor(Element.prototype, 'clientWidth');

const settings = (over = {}) => ({ locator: 'JO21SC', callsign: 'ON5GS', cqBands: new Set(['B23CM']), language: 'en', ...over });
/** Stand-in for the shared Live CQ store: the screen only reads getState() and subscribes. */
const fakeStore = () => {
  const store = { state: { spots: [] }, listeners: new Set() };
  store.getState = () => store.state;
  store.subscribe = (fn) => {
    store.listeners.add(fn);
    return () => store.listeners.delete(fn);
  };
  store.emit = (spots) => {
    store.state = { spots };
    store.listeners.forEach((fn) => fn(store.state));
  };
  return store;
};
let liveCq;
const ctxOf = (s, extra = {}) => ({ settings: s, t, now: () => Date.now(), liveCq, ...extra });
const q = (role) => host.querySelector(`[data-role="${role}"]`);

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  setLanguage('en');
  getContext = HTMLCanvasElement.prototype.getContext;
  drawCalls = 0;
  width = 375;
  HTMLCanvasElement.prototype.getContext = () => {
    drawCalls++;
    return null;
  };
  Object.defineProperty(HTMLCanvasElement.prototype, 'clientWidth', { get: () => width, configurable: true });
  liveCq = fakeStore();
  host = document.createElement('div');
  document.body.append(host);
  screen = createMoonScreen();
});
afterEach(() => {
  screen.unmount();
  vi.useRealTimers();
  vi.restoreAllMocks();
  HTMLCanvasElement.prototype.getContext = getContext;
  delete HTMLCanvasElement.prototype.clientWidth;
  host.remove();
});

describe('moon screen', () => {
  test('shows AZ and EL with 1 decimal for JO21SC at 2026-11-07T10:00:00Z', () => {
    screen.mount(host, ctxOf(settings()));
    expect(q('az').textContent).toBe('182.4°');
    expect(q('el').textContent).toBe('25.1°');
    expect(host.textContent).toContain('ON5GS  ·  JO21SC');
  });

  test('shows distance, phase and the event lines', () => {
    screen.mount(host, ctxOf(settings()));
    expect(q('distance').textContent).toMatch(/^Distance: \d{3},\d{3} km$/);
    expect(q('phase').textContent).toMatch(/^Moon phase: .+ \(\d+% lit\)$/);
    const lines = [...host.querySelectorAll('[data-role="event"]')].map((e) => e.querySelector('.label').textContent);
    expect(lines).toEqual([t('culmination'), t('moonset'), t('next_moonrise')]);
    expect(host.textContent).toContain(t('moon_up_now'));
    expect(host.querySelector('[data-role="event"] .time').textContent).toMatch(/^(.+ )?\d\d:\d\d$/);
  });

  test('clock ticks every second', () => {
    screen.mount(host, ctxOf(settings()));
    const first = q('utc').textContent;
    vi.advanceTimersByTime(1000);
    expect(q('utc').textContent).not.toBe(first);
  });

  test('no locator: message only, no numbers, no map, no NaN', () => {
    const canvasSpy = vi.spyOn(HTMLCanvasElement.prototype, 'getContext');
    screen.mount(host, ctxOf(settings({ locator: '' })));
    expect(host.textContent).toContain(t('no_locator'));
    expect(host.querySelector('canvas')).toBeNull();
    expect(q('az')).toBeNull();
    expect(host.textContent).not.toMatch(/NaN|AZ|EL|°|undefined/);
    vi.advanceTimersByTime(61_000);
    expect(host.textContent).not.toMatch(/NaN/);
    expect(canvasSpy).not.toHaveBeenCalled();
  });

  test('legend lists the chosen bands only, in band order', () => {
    screen.mount(host, ctxOf(settings({ cqBands: new Set(['B432', 'B144']) })));
    const labels = [...host.querySelectorAll('[data-role="legend"] .band')].map((e) => e.textContent);
    expect(labels).toEqual(['144', '432']);
  });

  test('timers pause while the page is hidden and resume when visible', () => {
    let state = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => state);
    screen.mount(host, ctxOf(settings()));
    state = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    const frozen = q('utc').textContent;
    vi.advanceTimersByTime(5000);
    expect(q('utc').textContent).toBe(frozen);
    state = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(q('utc').textContent).not.toBe(frozen);
  });

  test('unmount stops timers and removes listeners', () => {
    const remove = vi.spyOn(document, 'removeEventListener');
    screen.mount(host, ctxOf(settings()));
    screen.unmount();
    expect(remove).toHaveBeenCalledWith('visibilitychange', expect.any(Function));
    expect(vi.getTimerCount()).toBe(0);
  });

  test('resize redraws the map only when the width changed, coalesced per frame', () => {
    screen.mount(host, ctxOf(settings()));
    expect(drawCalls).toBe(1);
    window.dispatchEvent(new Event('resize'));
    vi.advanceTimersByTime(50);
    expect(drawCalls).toBe(1);
    width = 390;
    window.dispatchEvent(new Event('resize'));
    window.dispatchEvent(new Event('resize'));
    vi.advanceTimersByTime(50);
    expect(drawCalls).toBe(2);
  });

  test('redraws the map at once when the shared Live CQ spots change, and stops listening after unmount', () => {
    screen.mount(host, ctxOf(settings()));
    expect(drawCalls).toBe(1);
    expect(liveCq.listeners.size).toBe(1);
    liveCq.emit([]);
    expect(drawCalls).toBe(2);
    screen.unmount();
    expect(liveCq.listeners.size).toBe(0);
    liveCq.emit([]);
    expect(drawCalls).toBe(2);
  });
});

describe('own horizon in the moon tab (same as HorizonUseTest.kt)', () => {
  // 20 Nov 2026 12:00 UTC: the moon is still down. With ON5GS's horizon it is clear at the centre of JO21SC
  // from 15:50 to 02:15 UTC, in Brussels 16:50 to tomorrow 03:15.
  test('shows the state now and the window', () => {
    const restore = withTimeZone('Europe/Brussels');
    try {
      vi.setSystemTime(Date.parse('2026-11-20T12:00:00Z'));
      screen.mount(host, ctxOf(settings({ horizon: decode('1;5;B 60 110 25;P 180 200 20') })));
      expect(q('horizon-now').textContent).toBe('Moon behind your obstacles');
      expect(q('horizon-now').classList.contains('blocked')).toBe(true);
      const line = q('horizon-window');
      expect(line.textContent).toContain('Clear of your obstacles');
      expect(line.textContent).toContain('16:50');
      expect(line.textContent).toContain('tomorrow 03:15');
      expect(line.querySelector('.time').classList.contains('clear')).toBe(true);
    } finally {
      restore();
    }
  });

  test('the dish margin shortens the window: 6 m on 1296 MHz ends at 02:06 UTC, 03:06 in Brussels', () => {
    const restore = withTimeZone('Europe/Brussels');
    try {
      vi.setSystemTime(Date.parse('2026-11-20T12:00:00Z'));
      screen.mount(host, ctxOf(settings({ horizon: decode('1;5;B 60 110 25;P 180 200 20'), dishDiameterM: 6, dishBand: 'B1296', degBand: 'B144' })));
      const line = q('horizon-window');
      expect(line.textContent).toContain('16:50');
      expect(line.textContent).toContain('tomorrow 03:06');
    } finally {
      restore();
    }
  });

  test('without an own horizon nothing changes', () => {
    screen.mount(host, ctxOf(settings()));
    expect(q('horizon-now')).toBeNull();
    expect(q('horizon-window')).toBeNull();
  });
});

describe('slider to look ahead (#118, same as LookAheadScreenTest.kt)', () => {
  const HOUR = 3_600_000;
  const slide = (steps) => {
    const slider = q('look-ahead');
    slider.value = String(steps);
    slider.dispatchEvent(new Event('input'));
  };
  const deg = (v) => `${v.toFixed(1)}°`;

  test('6 hours ahead moves the moon, clock, phase and map, but not the times card; Now goes back', () => {
    const restore = withTimeZone('Europe/Brussels');
    try {
      const now = Date.parse('2026-11-20T12:00:00Z');
      vi.setSystemTime(now);
      const horizon = decode('1;5;B 60 110 25;P 180 200 20');
      screen.mount(host, ctxOf(settings({ horizon })));
      expect(q('look-label').textContent).toBe('Slide to look up to 48 h ahead');
      expect(q('look-now').disabled).toBe(true);
      expect(q('horizon-now').textContent).toBe('Moon behind your obstacles');
      const windowNow = q('horizon-window').textContent;

      slide(36);
      const { latDeg, lonDeg } = toLatLon('JO21SC');
      const later = position(latDeg, lonDeg, now + 6 * HOUR);
      expect(q('look-label').textContent).toBe('Ahead: +6:00');
      expect(q('look-now').disabled).toBe(false);
      expect(q('az').textContent).toBe(deg(later.azimuthDeg));
      expect(q('el').textContent).toBe(deg(later.elevationDeg));
      expect(q('utc').textContent).toContain('18:00:00');
      expect(q('horizon-now').textContent).not.toBe('Moon behind your obstacles');
      expect(mapCalls.at(-1).nowMs).toBe(now + 6 * HOUR);
      expect(q('horizon-window').textContent).toBe(windowNow);

      // The chosen time does not run on with the clock
      vi.advanceTimersByTime(5_000);
      expect(q('utc').textContent).toContain('18:00:00');

      q('look-now').click();
      const atNow = position(latDeg, lonDeg, Date.now());
      expect(q('az').textContent).toBe(deg(atNow.azimuthDeg));
      expect(q('look-label').textContent).toBe('Slide to look up to 48 h ahead');
      expect(q('horizon-now').textContent).toBe('Moon behind your obstacles');
    } finally {
      restore();
    }
  });

  test('the Live CQ spots and their legend are hidden when not on now', () => {
    liveCq.state = { spots: [{ callsign: 'PA0XYZ', band: 'B23CM', utcMillis: NOW - 5 * 60_000, spotter: 'W3SZ', grid: 'JO22', sources: new Set(['W3SZ']) }] };
    screen.mount(host, ctxOf(settings()));
    expect(mapCalls.at(-1).spots.map((s) => s.callsign)).toEqual(['PA0XYZ']);
    expect(q('legend').hidden).toBe(false);
    slide(1);
    expect(mapCalls.at(-1).spots).toEqual([]);
    expect(q('legend').hidden).toBe(true);
    liveCq.emit(liveCq.state.spots);
    expect(mapCalls.at(-1).spots).toEqual([]);
    q('look-now').click();
    expect(mapCalls.at(-1).spots.map((s) => s.callsign)).toEqual(['PA0XYZ']);
    expect(q('legend').hidden).toBe(false);
  });
});
