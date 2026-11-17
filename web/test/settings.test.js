import { afterEach, beforeEach, describe, expect, test } from 'vitest';
import { effectiveLanguage, loadSettings, locatorLatLon, saveSettings } from '../src/settings.js';

const DEFAULTS = {
  locator: '',
  callsign: '',
  cqBands: new Set(['B23CM']),
  degBand: 'B1296',
  chatBand: 'B1296',
  language: '',
  horizon: { minElDeg: 0, obstacles: [] },
  dishDiameterM: 0,
  dishBand: 'B1296',
  mutualLocators: [],
};

function fakeStorage() {
  const m = new Map();
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => void m.set(k, String(v)),
    removeItem: (k) => void m.delete(k),
  };
}
const throwing = () => {
  throw new Error('SecurityError');
};

const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
const setNavigatorLanguage = (language) =>
  Object.defineProperty(globalThis, 'navigator', { value: { language }, configurable: true });

beforeEach(() => {
  globalThis.localStorage = fakeStorage();
  setNavigatorLanguage('en-US');
});
afterEach(() => {
  delete globalThis.localStorage;
  if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator);
  else delete globalThis.navigator;
});

describe('loadSettings', () => {
  test('empty localStorage gives the defaults', () => {
    expect(loadSettings()).toEqual(DEFAULTS);
  });

  test('missing localStorage gives the defaults', () => {
    delete globalThis.localStorage;
    expect(loadSettings()).toEqual(DEFAULTS);
    expect(() => saveSettings({ locator: 'JO20' })).not.toThrow();
  });

  test('localStorage that throws on access gives the defaults', () => {
    Object.defineProperty(globalThis, 'localStorage', { get: throwing, configurable: true });
    expect(loadSettings()).toEqual(DEFAULTS);
    expect(() => saveSettings({ locator: 'JO20' })).not.toThrow();
  });

  test('localStorage whose methods throw gives the defaults', () => {
    globalThis.localStorage = { getItem: throwing, setItem: throwing };
    expect(loadSettings()).toEqual(DEFAULTS);
    expect(() => saveSettings({ locator: 'JO20' })).not.toThrow();
  });

  test('corrupt stored value gives the defaults', () => {
    localStorage.setItem('eme.settings', '{nope');
    expect(loadSettings()).toEqual(DEFAULTS);
    localStorage.setItem('eme.settings', '[1]');
    expect(loadSettings()).toEqual(DEFAULTS);
  });

  test('default and unknown stored language mean automatic ("")', () => {
    expect(loadSettings().language).toBe('');
    localStorage.setItem('eme.settings', JSON.stringify({ language: 'xx' }));
    expect(loadSettings().language).toBe('');
  });
});

describe('effectiveLanguage', () => {
  test('automatic follows the browser language when it is one of the 13, else en', () => {
    setNavigatorLanguage('nl-BE');
    expect(effectiveLanguage({ language: '' })).toBe('nl');
    setNavigatorLanguage('tr-TR');
    expect(effectiveLanguage({ language: '' })).toBe('en');
  });

  test('a chosen language wins over the browser', () => {
    setNavigatorLanguage('nl-BE');
    expect(effectiveLanguage({ language: 'fr' })).toBe('fr');
  });
});

describe('saveSettings', () => {
  test('round trip, partial updates keep other fields', () => {
    saveSettings({ locator: 'JO20ab', cqBands: new Set(['B144', 'B432']) });
    saveSettings({ callsign: 'ON8AR', degBand: 'B432', language: 'fr' });
    expect(loadSettings()).toEqual({
      locator: 'JO20ab',
      callsign: 'ON8AR',
      cqBands: new Set(['B144', 'B432']),
      degBand: 'B432',
      chatBand: 'B1296',
      language: 'fr',
      horizon: { minElDeg: 0, obstacles: [] },
      dishDiameterM: 0,
      dishBand: 'B1296',
      mutualLocators: [],
    });
  });

  test('unknown band ids and languages are dropped on load; empty band set stays empty', () => {
    localStorage.setItem(
      'eme.settings',
      JSON.stringify({ cqBands: ['B144', 'nope'], degBand: 'nope', language: 'xx', locator: 5 }),
    );
    expect(loadSettings()).toEqual({ ...DEFAULTS, cqBands: new Set(['B144']) });
    saveSettings({ cqBands: new Set() });
    expect(loadSettings().cqBands).toEqual(new Set());
  });
});

describe('locatorLatLon', () => {
  test('null for empty and invalid locators', () => {
    expect(locatorLatLon({ locator: '' })).toBeNull();
    expect(locatorLatLon({ locator: 'XX99' })).toBeNull();
    expect(locatorLatLon({ locator: 'JO2' })).toBeNull();
  });

  test('valid locator gives lat/lon of the square centre', () => {
    expect(locatorLatLon({ locator: 'JO20' })).toEqual({ latDeg: 50.5, lonDeg: 5 });
  });
});

describe('chatBand', () => {
  test('defaults to B1296; unknown name falls back; a valid name is kept', () => {
    expect(loadSettings().chatBand).toBe('B1296');
    localStorage.setItem('eme.settings', JSON.stringify({ chatBand: 'nope' }));
    expect(loadSettings().chatBand).toBe('B1296');
    saveSettings({ chatBand: 'B432' });
    expect(loadSettings().chatBand).toBe('B432');
  });
});

describe('own horizon', () => {
  test('is stored as text like the app and read back', () => {
    const mine = { minElDeg: 5, obstacles: [{ fromAzDeg: 60, toAzDeg: 110, maxElDeg: 25, kind: 'B' }] };
    saveSettings({ horizon: mine });
    expect(JSON.parse(localStorage.getItem('eme.settings')).horizon).toBe('1;5;B 60 110 25');
    expect(loadSettings().horizon).toEqual(mine);
  });

  test('rubbish falls back to no horizon', () => {
    localStorage.setItem('eme.settings', JSON.stringify({ horizon: '9;nonsense' }));
    expect(loadSettings().horizon).toEqual({ minElDeg: 0, obstacles: [] });
  });
});

describe('dish diameter', () => {
  test('is stored and read back; rubbish means no dish', () => {
    saveSettings({ dishDiameterM: 6 });
    expect(loadSettings().dishDiameterM).toBe(6);
    for (const bad of ['6', -1, 150, null]) {
      localStorage.setItem('eme.settings', JSON.stringify({ dishDiameterM: bad }));
      expect(loadSettings().dishDiameterM).toBe(0);
    }
  });

  test('band of the dish: 1296 by default, kept, unknown ids dropped', () => {
    expect(loadSettings().dishBand).toBe('B1296');
    saveSettings({ dishBand: 'B432' });
    expect(loadSettings().dishBand).toBe('B432');
    localStorage.setItem('eme.settings', JSON.stringify({ dishBand: 'B10G' }));
    expect(loadSettings().dishBand).toBe('B1296');
  });
});

describe('recent mutual window locators', () => {
  test('are kept; invalid ones and more than 5 are dropped', () => {
    saveSettings({ mutualLocators: ['FN32', 'QF22'] });
    expect(loadSettings().mutualLocators).toEqual(['FN32', 'QF22']);
    localStorage.setItem('eme.settings', JSON.stringify({ mutualLocators: ['FN32', 'xx', 5, '', 'QF22', 'RE78', 'JO21', 'KO85', 'PM95'] }));
    expect(loadSettings().mutualLocators).toEqual(['FN32', 'QF22', 'RE78', 'JO21', 'KO85']);
  });
});
