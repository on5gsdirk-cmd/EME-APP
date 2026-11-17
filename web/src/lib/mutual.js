// Mutual moon windows with another station (#119). Same rules as horizon/MutualWindows.kt;
// shared reference: app/src/test/resources/expected/mutual_window.csv.
import { position } from './moon-calculator.js';
import { VISIBILITY, visibility } from './horizon.js';

const STEP_MS = 60_000;
const DAY_MS = 86_400_000;

/**
 * The moon at the same time clear at home (own obstacles, minimum and dish margin) and above 0 deg at the
 * other station. Returns [{ startMillis, endMillis, maxElDeg, maxAtMillis, partial }], endMillis not included;
 * maxElDeg = highest point of the lower of the two moon elevations.
 */
export function mutualWindows(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, startMillis, endMillis) {
  const out = [];
  let open = null;
  for (let t = startMillis; t < endMillis; t += STEP_MS) {
    const home = position(homeLat, homeLon, t);
    const v = visibility(horizon, home.azimuthDeg, home.elevationDeg, marginDeg);
    const dx = position(dxLat, dxLon, t);
    if (v !== VISIBILITY.BLOCKED && dx.elevationDeg > 0) {
      const mutual = Math.min(home.elevationDeg, dx.elevationDeg);
      if (open === null) open = { startMillis: t, maxElDeg: mutual, maxAtMillis: t, partial: false };
      if (mutual > open.maxElDeg) {
        open.maxElDeg = mutual;
        open.maxAtMillis = t;
      }
      if (v === VISIBILITY.PARTIAL) open.partial = true;
    } else if (open !== null) {
      out.push({ ...open, endMillis: t });
      open = null;
    }
  }
  if (open !== null) out.push({ ...open, endMillis });
  return out;
}

/** How far nextMutualWindows looks past the edges for the real start or end; the moon is nowhere up longer than about 14 days. */
export const EDGE_DAYS = 15;

const isMutual = (horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, t) => {
  const home = position(homeLat, homeLon, t);
  return visibility(horizon, home.azimuthDeg, home.elevationDeg, marginDeg) !== VISIBILITY.BLOCKED && position(dxLat, dxLon, t).elevationDeg > 0;
};

/**
 * The mutual windows still running after nowMillis that start within `days` days, minute by minute. A window
 * running now gets its real start (searched back, also further than a day), the last window its real end, also
 * after the `days` days (#120); both at most EDGE_DAYS away. Same as MutualWindows.next in Kotlin.
 */
export function nextMutualWindows(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, nowMillis, days = 30) {
  const start = nowMillis - (nowMillis % STEP_MS);
  const end = start + days * DAY_MS;
  // A window running now: back to its real start
  let from = start;
  while (from > start - EDGE_DAYS * DAY_MS && isMutual(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, from - STEP_MS)) from -= STEP_MS;
  const windows = mutualWindows(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, from, end);
  // The last window still open at the end: on until it really closes
  const last = windows.at(-1);
  if (last && last.endMillis === end) {
    const rest = mutualWindows(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, end, end + EDGE_DAYS * DAY_MS)[0];
    if (rest && rest.startMillis === end) {
      const higher = rest.maxElDeg > last.maxElDeg ? rest : last;
      windows[windows.length - 1] = { ...last, endMillis: rest.endMillis, maxElDeg: higher.maxElDeg, maxAtMillis: higher.maxAtMillis, partial: last.partial || rest.partial };
    }
  }
  return windows.filter((w) => w.endMillis > nowMillis);
}

/** The last locators used for the mutual window: newest first, no duplicates, at most max (as addRecentLocator in Kotlin). */
export function addRecentLocator(recent, locator, max = 5) {
  const up = locator.toUpperCase();
  return [up, ...recent.filter((l) => l.toUpperCase() !== up)].slice(0, max);
}
