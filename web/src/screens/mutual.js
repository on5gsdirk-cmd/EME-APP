// "Mutual window" (moon/MutualWindowScreen.kt, #119): the moon at the same time clear at home and above 0 deg
// at another station, the next 30 days, with the max. mutual elevation and the degradation; recent locators.
import { addRecentLocator, nextMutualWindows } from '../lib/mutual.js';
import { marginForSettings, defaultHorizon } from '../lib/horizon.js';
import { EME_BANDS, computeDegradation } from '../lib/degradation.js';
import { isValid, normalize, toLatLon } from '../lib/maidenhead.js';
import { saveSettings } from '../settings.js';

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
const role = (node, r) => {
  node.dataset.role = r;
  return node;
};
const two = (n) => String(n).padStart(2, '0');
const condClass = (condition) => `cond-${condition.toLowerCase().replace('_', '-')}`;

/**
 * nextMutualWindows in a Web Worker, so the page does not freeze (#120); without Worker support (or when it
 * fails) on the main thread after one tick, so "Calculating…" shows first.
 */
function computeWindows(args) {
  const direct = () => nextMutualWindows(...args);
  return new Promise((resolve) => {
    if (typeof Worker === 'undefined') {
      setTimeout(() => resolve(direct()), 0);
      return;
    }
    const worker = new Worker(new URL('../lib/mutual-worker.js', import.meta.url), { type: 'module' });
    const done = (result) => {
      worker.terminate();
      resolve(result);
    };
    worker.onmessage = (e) => done(e.data);
    worker.onerror = () => done(direct());
    worker.postMessage(args);
  });
}

/** Short name of the local time zone at that moment ("CET" in winter, "CEST" in summer). */
function zoneName(nowMs, lang) {
  return new Intl.DateTimeFormat(lang, { timeZoneName: 'short' }).formatToParts(nowMs).find((p) => p.type === 'timeZoneName')?.value ?? '';
}

/**
 * One table row (card): day, local times with "(+1)" past midnight, duration, max. EL and Deg. The time zone
 * of the window when it is not the one in the heading (after a change to or from summer time, #120).
 */
function card(w, deg, mhz, lang, t, headZone) {
  const s = new Date(w.startMillis);
  const e = new Date(w.endMillis);
  const day = `${new Intl.DateTimeFormat(lang, { weekday: 'short' }).format(s)} ${two(s.getDate())}-${two(s.getMonth() + 1)}`;
  const dayIndex = (d) => Math.floor((d.getTime() - d.getTimezoneOffset() * 60_000) / 86_400_000);
  const plus = dayIndex(e) - dayIndex(s);
  const minutes = Math.round((w.endMillis - w.startMillis) / 60_000);
  const hm = (d) => `${two(d.getHours())}:${two(d.getMinutes())}`;
  const zone = zoneName(w.startMillis, lang);
  const row = role(el('div', 'card mutual-row'), 'mutual-row');
  row.append(
    el('div', 'when', `${day}   ${hm(s)} – ${hm(e)}${plus > 0 ? ` (+${plus})` : ''}${zone !== headZone ? ` ${zone}` : ''}   (${Math.floor(minutes / 60)}:${two(minutes % 60)})`),
    el('div', `max ${condClass(deg.condition)}`, t('mutual_max', w.maxElDeg.toFixed(1), deg.degDb.toFixed(1), String(mhz))),
  );
  if (w.partial) row.append(el('div', 'partial small', t('mutual_partial')));
  return row;
}

/**
 * The panel, shown over the Moon tab. ctx: { settings, t, now, lang }; onClose() removes it.
 * Calculating 30 days takes a few hundred ms: in a Web Worker, "Calculating…" shows meanwhile.
 */
export function createMutualPanel({ settings, t, now, lang }, onClose) {
  const home = toLatLon(settings.locator);
  const band = EME_BANDS.find((b) => b.id === settings.degBand) ?? EME_BANDS.find((b) => b.id === 'B1296');
  let recent = [...(settings.mutualLocators ?? [])];
  let busy = false;

  const panel = role(el('div', 'mutual-panel'), 'mutual-panel');
  const back = role(el('button', 'text-button', `← ${t('back')}`), 'mutual-back');
  back.type = 'button';
  back.addEventListener('click', onClose);
  const headZone = zoneName(now(), lang);
  panel.append(back, el('h2', '', t('mutual_title')), el('div', 'support', t('mutual_info', headZone)));
  if (!home) {
    panel.append(el('div', 'error', t('no_locator')));
    return panel;
  }

  const input = role(el('input'), 'mutual-locator');
  Object.assign(input, { type: 'text', value: recent[0] ?? '', spellcheck: false, autocapitalize: 'characters' });
  const support = el('div', 'support');
  const field = el('label', 'field');
  field.append(t('mutual_locator'), input, support);
  const recentRow = el('div', 'chips recent');
  const calc = role(el('button', 'button', t('mutual_calculate')), 'mutual-calculate');
  calc.type = 'button';
  const status = role(el('div', 'support'), 'mutual-status');
  const results = role(el('div', 'mutual-results'), 'mutual-results');

  const refresh = () => {
    const v = input.value;
    const ok = isValid(v);
    field.classList.toggle('invalid', v !== '' && !ok);
    support.textContent = v !== '' && !ok ? t('locator_invalid') : '';
    calc.disabled = !ok || busy;
    recentRow.replaceChildren();
    if (recent.length) {
      recentRow.append(el('span', 'small', t('mutual_recent')));
      for (const l of recent) {
        const b = role(el('button', 'chip', l), 'mutual-recent');
        b.type = 'button';
        b.disabled = busy;
        b.addEventListener('click', () => {
          input.value = l;
          calculate(l);
        });
        recentRow.append(b);
      }
    }
  };

  function calculate(locator) {
    const dx = toLatLon(locator);
    if (!dx || busy) return;
    busy = true;
    status.textContent = t('mutual_calculating');
    results.replaceChildren();
    refresh();
    const horizon = settings.horizon ?? defaultHorizon();
    computeWindows([horizon, marginForSettings(settings, EME_BANDS), home.latDeg, home.lonDeg, dx.latDeg, dx.lonDeg, now()]).then((ws) => {
      const pair = role(el('h3', '', `${settings.locator}  ↔  ${normalize(locator)}`), 'mutual-pair');
      results.replaceChildren(pair);
      if (ws.length === 0) results.append(role(el('div', '', t('mutual_none')), 'mutual-none'));
      for (const w of ws) {
        const deg = computeDegradation(w.maxAtMillis, home.latDeg, home.lonDeg, band, band.defaultTsys);
        results.append(card(w, deg, band.mhz, lang, t, headZone));
      }
      recent = addRecentLocator(recent, normalize(locator));
      settings.mutualLocators = recent;
      saveSettings({ mutualLocators: recent });
      busy = false;
      status.textContent = '';
      refresh();
    });
  }

  input.addEventListener('input', () => {
    input.value = input.value.trim().toUpperCase().slice(0, 6);
    refresh();
  });
  calc.addEventListener('click', () => calculate(input.value));
  panel.append(field, recentRow, calc, status, results);
  refresh();
  return panel;
}
