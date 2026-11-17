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

/** The mutual windows still running after nowMillis, up to `days` days ahead; searched from 24 h back. */
export function nextMutualWindows(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, nowMillis, days = 30) {
  const start = nowMillis - (nowMillis % STEP_MS);
  return mutualWindows(horizon, marginDeg, homeLat, homeLon, dxLat, dxLon, start - DAY_MS, start + days * DAY_MS).filter((w) => w.endMillis > nowMillis);
}

/** The last locators used for the mutual window: newest first, no duplicates, at most max (as addRecentLocator in Kotlin). */
export function addRecentLocator(recent, locator, max = 5) {
  const up = locator.toUpperCase();
  return [up, ...recent.filter((l) => l.toUpperCase() !== up)].slice(0, max);
}
