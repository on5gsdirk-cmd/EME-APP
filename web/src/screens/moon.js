// Port of MoonScreen.kt: date/time, world map, band legend, phase, AZ/EL, distance and moonrise/culmination/moonset.
// Clock every second, events and map every minute; both pause while the page is hidden.

import { liveCqStore } from '../data.js';
import { effectiveLanguage, locatorLatLon } from '../settings.js';
import { createMutualPanel } from './mutual.js';
import { VISIBILITY, currentOrNextWindow, defaultHorizon, isDefaultHorizon, marginForSettings, visibility } from '../lib/horizon.js';
import { EME_BANDS } from '../lib/degradation.js';
import { BANDS, selectSpots, stationLatLon } from '../lib/livecq.js';
import { computeEvents } from '../lib/moon-events.js';
import { position } from '../lib/moon-calculator.js';
import { phaseAt } from '../lib/moon-phase.js';
import { bandVar, drawWorldMap, parseLand } from '../lib/world-map.js';
import landText from '../generated/land110m.txt?raw';

const TICK_MS = 1000;
const MINUTE_MS = 60_000;
/** CQ stations on the map: same selection as the CQ list (last hour). */
const CQ_MAX_AGE_MINUTES = 60;

let landCache = null;
const land = () => (landCache ??= parseLand(landText));

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
const tagged = (node, role) => {
  node.dataset.role = role;
  return node;
};
const two = (n) => String(n).padStart(2, '0');

/** "EEE dd-MM-yyyy  HH:mm:ss" of the Kotlin formatter; local (utc = false) or UTC. */
function formatDateTime(ms, lang, utc) {
  const d = new Date(ms);
  const weekday = new Intl.DateTimeFormat(lang, { weekday: 'short', timeZone: utc ? 'UTC' : undefined }).format(d);
  const g = utc
    ? { y: d.getUTCFullYear(), mo: d.getUTCMonth() + 1, d: d.getUTCDate(), h: d.getUTCHours(), mi: d.getUTCMinutes(), s: d.getUTCSeconds() }
    : { y: d.getFullYear(), mo: d.getMonth() + 1, d: d.getDate(), h: d.getHours(), mi: d.getMinutes(), s: d.getSeconds() };
  return `${weekday} ${two(g.d)}-${two(g.mo)}-${g.y}  ${two(g.h)}:${two(g.mi)}:${two(g.s)}`;
}

function zoneName(ms, lang) {
  const part = new Intl.DateTimeFormat(lang, { timeZoneName: 'short' }).formatToParts(new Date(ms)).find((p) => p.type === 'timeZoneName');
  return part ? part.value : '';
}

const dayNumber = (d) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86_400_000;

/** "21:45", or "tomorrow 06:05" / "yesterday 23:10" / "Mon 06:05" when not today (browser time zone). */
export function localTime(utcMillis, nowMs, tomorrow, yesterday, lang) {
  const t = new Date(utcMillis);
  const diff = dayNumber(t) - dayNumber(new Date(nowMs));
  const prefix =
    diff === 0 ? '' : diff === 1 ? `${tomorrow} ` : diff === -1 ? `${yesterday} ` : `${new Intl.DateTimeFormat(lang, { weekday: 'short' }).format(t)} `;
  return `${prefix}${two(t.getHours())}:${two(t.getMinutes())}`;
}

/** Screen contract { mount(el, ctx), unmount() }; the CQ spots for the map come from the shared Live CQ store (ctx.liveCq, default the shared one). */
export function createMoonScreen() {
  let timer = null;
  let cleanup = null;

  return {
    mount(host, ctx) {
      const { settings, t } = ctx;
      const liveCq = ctx.liveCq ?? liveCqStore;
      const lang = effectiveLanguage(settings);
      const pos = locatorLatLon(settings);
      const callsign = settings.callsign;
      // Own horizon (settings > My horizon); nothing changes without one, as in MoonScreen.kt
      const horizon = settings.horizon ?? defaultHorizon();
      const withHorizon = !isDefaultHorizon(horizon);
      const margin = marginForSettings(settings, EME_BANDS); // dish margin, 0 without a dish

      const root = el('div', 'moon');
      const timeLines = el('div', 'timelines');
      const localValue = tagged(el('span', 'value'), 'local');
      const localZone = el('span', 'zone');
      const utcValue = tagged(el('span', 'value'), 'utc');
      const localText = el('span');
      localValue.append(localText, localZone);
      timeLines.append(el('span', 'label', t('time_local')), localValue, el('span', 'label', 'UTC'), utcValue);
      root.append(timeLines);
      host.replaceChildren(root);

      const tickClock = (now) => {
        localText.textContent = formatDateTime(now, lang, false);
        localZone.textContent = zoneName(now, lang);
        utcValue.textContent = formatDateTime(now, lang, true);
      };

      if (!pos) {
        root.append(el('div', 'error', t('no_locator')));
        const tick = () => tickClock(ctx.now());
        tick();
        start(tick);
        return;
      }

      const canvas = el('canvas');
      const legend = tagged(el('div', 'legend'), 'legend');
      legend.append(el('span', '', t('livecq_spots')));
      if (settings.cqBands.size === 0) legend.append(el('span', '', '—'));
      for (const band of BANDS.filter((b) => settings.cqBands.has(b.id))) {
        const s = el('span', 'band', band.label);
        s.style.color = `var(${bandVar(band.id)})`;
        legend.append(s);
      }
      const phase = tagged(el('div', 'phase'), 'phase');
      const where = el('div', 'where', `${callsign}  ·  ${settings.locator}`);
      const az = tagged(el('span', 'num'), 'az');
      const elev = tagged(el('span', 'num'), 'el');
      const bigValues = el('div', 'bigvalues');
      for (const [caption, num] of [['AZ', az], ['EL', elev]]) {
        const box = el('div', 'bigvalue');
        box.append(el('span', 'caption', caption), num);
        bigValues.append(box);
      }
      const distance = tagged(el('div', 'distance'), 'distance');
      const horizonNow = tagged(el('div', 'horizon-now'), 'horizon-now');
      const events = el('div', 'events');
      // Mutual moon window with another station (#119): a panel over the Moon tab
      const mutualOpen = tagged(el('button', 'button outlined mutual-open', t('mutual_button')), 'mutual-open');
      mutualOpen.type = 'button';
      mutualOpen.addEventListener('click', () => {
        const panel = createMutualPanel({ settings, t, now: ctx.now, lang }, () => panel.remove());
        root.append(panel);
      });
      root.append(canvas, legend, phase, where, bigValues, distance, ...(withHorizon ? [horizonNow] : []), events, mutualOpen);

      let eventData = null;
      let windowData = null;
      let lastMinute = null;

      const phaseName = (i) => t(`phase_${i}`);
      const line = (label, event, now, extra) => {
        const row = tagged(el('div', 'event'), 'event');
        row.append(el('span', 'label', label));
        if (event === null) {
          row.append(el('span', 'none', t('not_within_48h')));
        } else {
          if (event.utcMillis < now) row.classList.add('past');
          row.append(
            el('span', 'time', localTime(event.utcMillis, now, t('tomorrow'), t('yesterday'), lang)),
            el('span', 'extra', extra(event)),
          );
        }
        return row;
      };
      const azText = (e) => `AZ ${e.azimuthDeg.toFixed(0)}°`;
      const elText = (e) => `EL ${e.elevationDeg.toFixed(1)}°`;

      const renderEvents = (now) => {
        if (!eventData) return;
        const ev = eventData;
        const head = el('div', 'head', t('times_local', zoneName(now, lang)));
        const rows = ev.isUp
          ? [el('div', 'up', t('moon_up_now')), line(t('culmination'), ev.culmination, now, elText), line(t('moonset'), ev.nextSet, now, azText), line(t('next_moonrise'), ev.nextRise, now, azText)]
          : [line(t('moonrise'), ev.nextRise, now, azText), line(t('culmination'), ev.culmination, now, elText), line(t('moonset'), ev.nextSet, now, azText)];
        if (withHorizon) rows.push(horizonLine(windowData, now));
        events.replaceChildren(head, ...rows);
      };
      const VIS_CLASS = { [VISIBILITY.CLEAR]: 'clear', [VISIBILITY.PARTIAL]: 'partial', [VISIBILITY.BLOCKED]: 'blocked' };
      const VIS_TEXT = { [VISIBILITY.CLEAR]: 'horizon_moon_clear', [VISIBILITY.PARTIAL]: 'horizon_moon_partial', [VISIBILITY.BLOCKED]: 'horizon_moon_blocked' };
      // "Clear of your horizon   15:55 – tomorrow 02:20", green (clear) or orange (partly with loss)
      const horizonLine = (w, now) => {
        const row = tagged(el('div', 'event horizon-window'), 'horizon-window');
        row.append(el('span', 'label', t('horizon_window')));
        if (w === null) row.append(el('span', 'none', t('not_within_48h')));
        else {
          const fmt = (ms) => localTime(ms, now, t('tomorrow'), t('yesterday'), lang);
          row.append(el('span', `time ${VIS_CLASS[w.visibility]}`, `${fmt(w.startMillis)} – ${fmt(w.endMillis)}`));
        }
        return row;
      };

      const redrawMap = (now) => {
        // One dot per station and band, same selection as the Live CQ tab
        const stations = selectSpots(liveCq.getState().spots, settings.cqBands, now, CQ_MAX_AGE_MINUTES).flatMap((s) => {
          const p = s.band ? stationLatLon(s.grid) : null;
          return p ? [{ callsign: s.callsign, latDeg: p.latDeg, lonDeg: p.lonDeg, band: s.band }] : [];
        });
        drawWorldMap(canvas, { nowMs: now, home: { latDeg: pos.latDeg, lonDeg: pos.lonDeg, callsign }, spots: stations, land: land() });
      };

      const tick = () => {
        const now = ctx.now();
        tickClock(now);
        const moon = position(pos.latDeg, pos.lonDeg, now);
        az.textContent = `${moon.azimuthDeg.toFixed(1)}°`;
        elev.textContent = `${moon.elevationDeg.toFixed(1)}°`;
        distance.textContent = t('distance', moon.distanceKm.toLocaleString(lang, { maximumFractionDigits: 0 }));
        if (withHorizon) {
          const v = visibility(horizon, moon.azimuthDeg, moon.elevationDeg, margin);
          horizonNow.className = `horizon-now ${VIS_CLASS[v]}`;
          horizonNow.textContent = t(VIS_TEXT[v]);
        }
        const minute = Math.floor(now / MINUTE_MS);
        if (minute !== lastMinute) {
          lastMinute = minute;
          eventData = computeEvents(pos.latDeg, pos.lonDeg, now);
          if (withHorizon) windowData = currentOrNextWindow(horizon, pos.latDeg, pos.lonDeg, now, margin);
          const p = phaseAt(now);
          phase.textContent = t('moon_phase', phaseName(p.nameIndex), Math.round(p.illuminated * 100));
          redrawMap(now);
        }
        renderEvents(now);
      };
      // resize also fires when the mobile URL bar collapses (height only): redraw only for a new width, once per frame
      let drawnWidth = canvas.clientWidth;
      let frame = null;
      const onResize = () => {
        if (frame !== null) return;
        frame = requestAnimationFrame(() => {
          frame = null;
          if (canvas.clientWidth === drawnWidth) return;
          drawnWidth = canvas.clientWidth;
          redrawMap(ctx.now());
        });
      };
      window.addEventListener('resize', onResize);
      // New spots: redraw the map at once, not only at the next minute (not for loading or error changes)
      let drawnSpots = liveCq.getState().spots;
      const unsubscribe = liveCq.subscribe((state) => {
        if (state.spots === drawnSpots) return;
        drawnSpots = state.spots;
        redrawMap(ctx.now());
      });
      cleanup = () => {
        unsubscribe();
        window.removeEventListener('resize', onResize);
        if (frame !== null) cancelAnimationFrame(frame);
      };
      tick();
      drawnWidth = canvas.clientWidth;
      start(tick);
    },

    unmount() {
      stop();
      cleanup?.();
      cleanup = null;
    },
  };

  /** Runs tick every second while the page is visible; pauses when hidden, catches up when visible again. */
  function start(tick) {
    const sync = () => {
      stop();
      if (document.visibilityState === 'visible') {
        tick();
        timer = setInterval(tick, TICK_MS);
      }
    };
    document.addEventListener('visibilitychange', sync);
    const prev = cleanup;
    cleanup = () => {
      document.removeEventListener('visibilitychange', sync);
      prev?.();
    };
    if (document.visibilityState === 'visible') timer = setInterval(tick, TICK_MS);
  }

  function stop() {
    if (timer !== null) clearInterval(timer);
    timer = null;
  }
}
