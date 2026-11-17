import { LANGUAGES } from './i18n.js';
import { HB9Q_BANDS } from './lib/hb9q.js';
import { BANDS } from './lib/livecq.js';
import { EME_BANDS } from './lib/degradation.js';
import { isValid, toLatLon } from './lib/maidenhead.js';
import { decode as decodeHorizon, defaultHorizon, encode as encodeHorizon } from './lib/horizon.js';

// One JSON object under one key; defaults as in SettingsRepository.kt (locator and callsign empty since #55).
const KEY = 'eme.settings';

export const browserLanguage = () => {
  const code = (typeof navigator !== 'undefined' && navigator.language ? navigator.language : '').slice(0, 2).toLowerCase();
  return LANGUAGES.includes(code) ? code : 'en';
};

const defaults = () => ({
  locator: '',
  callsign: '',
  cqBands: new Set(['B23CM']),
  degBand: 'B1296',
  chatBand: 'B1296',
  language: '', // '' = automatic: follow the browser (effectiveLanguage)
  horizon: defaultHorizon(), // own horizon, stored as text like the app (lib/horizon.js)
  dishDiameterM: 0, // dish diameter in m for the margin above the own horizon; 0 = no margin (e.g. Yagis)
  dishBand: 'B1296', // band the dish is used on, for the margin (not the band of the Deg tab)
  mutualLocators: [], // last locators used in the mutual window, newest first, at most 5
});

/** Stored JSON object, or {} when localStorage is missing, throws or holds rubbish. */
function readStored() {
  try {
    const v = JSON.parse(localStorage.getItem(KEY));
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {};
  } catch {
    return {};
  }
}

export function loadSettings() {
  const s = readStored();
  const d = defaults();
  return {
    locator: typeof s.locator === 'string' ? s.locator : d.locator,
    callsign: typeof s.callsign === 'string' ? s.callsign : d.callsign,
    cqBands: Array.isArray(s.cqBands) ? new Set(s.cqBands.filter((id) => BANDS.some((b) => b.id === id))) : d.cqBands,
    degBand: EME_BANDS.some((b) => b.id === s.degBand) ? s.degBand : d.degBand,
    chatBand: HB9Q_BANDS.some((b) => b.name === s.chatBand) ? s.chatBand : d.chatBand,
    language: LANGUAGES.includes(s.language) ? s.language : d.language,
    horizon: decodeHorizon(s.horizon) ?? d.horizon,
    dishDiameterM: Number.isFinite(s.dishDiameterM) && s.dishDiameterM >= 0 && s.dishDiameterM <= 100 ? s.dishDiameterM : d.dishDiameterM,
    dishBand: EME_BANDS.some((b) => b.id === s.dishBand) ? s.dishBand : d.dishBand,
    mutualLocators: Array.isArray(s.mutualLocators) ? s.mutualLocators.filter((l) => typeof l === 'string' && l !== '' && isValid(l)).slice(0, 5) : d.mutualLocators,
  };
}

/** Language to render in: the chosen one, or the browser language (else en) for automatic (''). */
export function effectiveLanguage(settings) {
  return LANGUAGES.includes(settings.language) ? settings.language : browserLanguage();
}

export function saveSettings(partial) {
  const next = { ...readStored(), ...partial };
  if (next.cqBands instanceof Set) next.cqBands = [...next.cqBands];
  if (next.horizon && typeof next.horizon === 'object') next.horizon = encodeHorizon(next.horizon);
  try {
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // storage unavailable (private mode, quota): settings stay in memory of the caller only
  }
}

/** null for an empty or invalid locator. */
export function locatorLatLon(settings) {
  return isValid(settings.locator) ? toLatLon(settings.locator) : null;
}
