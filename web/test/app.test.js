// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { mountApp } from '../src/app.js';
import { setLanguage, t } from '../src/i18n.js';
import { loadSettings } from '../src/settings.js';
import strings from '../src/generated/strings.json';

let root;
let navigatorDescriptor;
let getContext;

beforeEach(() => {
  getContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = () => null; // jsdom has no canvas
  const m = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => void m.set(k, String(v)),
  });
  navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  Object.defineProperty(globalThis, 'navigator', { value: { language: 'fr-BE' }, configurable: true });
  vi.stubGlobal('fetch', () => new Promise(() => {})); // the Moon tab subscribes to the Live CQ store: no network in tests
  localStorage.setItem('eme.settings', JSON.stringify({ language: 'en' }));
  document.body.innerHTML = '<div id="app"></div>';
  root = document.getElementById('app');
});

afterEach(() => {
  HTMLCanvasElement.prototype.getContext = getContext;
  vi.unstubAllGlobals();
  if (navigatorDescriptor) Object.defineProperty(globalThis, 'navigator', navigatorDescriptor);
  else delete globalThis.navigator;
  setLanguage('en');
});

const tabs = () => [...root.querySelectorAll('[role="tab"]')];
const openSettings = () => root.querySelector('[data-action="settings"]').click();
const type = (el, value) => {
  el.value = value;
  el.dispatchEvent(new Event('input', { bubbles: true }));
};

describe('shell', () => {
  test('title shows the app title and v + version', () => {
    mountApp(root);
    expect(root.querySelector('header').textContent).toContain(t('app_title'));
    expect(root.querySelector('[data-role="version"]').textContent).toBe('v' + __APP_VERSION__);
  });

  test('five tabs in order Moon, Deg, HB9Q, N0UK, Live CQ with texts from strings.json', () => {
    mountApp(root);
    expect(tabs().map((x) => x.textContent)).toEqual([t('tab_moon'), t('tab_degradation'), t('tab_chat'), t('tab_n0uk'), t('tab_livecq')]);
    expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
  });

  test('a tab mounts its screen and unmounts the previous one', () => {
    const calls = [];
    const screen = (name) => () => ({
      mount: (el, ctx) => {
        calls.push('mount ' + name);
        expect(typeof ctx.now()).toBe('number');
        expect(ctx.t).toBe(t);
      },
      unmount: () => calls.push('unmount ' + name),
    });
    mountApp(root, { moon: screen('moon'), deg: screen('deg'), chat: screen('chat'), n0uk: screen('n0uk'), livecq: screen('livecq') });
    tabs()[3].click();
    expect(calls).toEqual(['mount moon', 'unmount moon', 'mount n0uk']);
    expect(tabs()[3].getAttribute('aria-selected')).toBe('true');
  });

  test('settings icon opens Settings, hides the tabs; back returns', () => {
    mountApp(root);
    openSettings();
    expect(tabs()).toHaveLength(0);
    expect(root.querySelector('header').textContent).toContain(t('settings'));
    root.querySelector('[data-action="back"]').click();
    expect(tabs()).toHaveLength(5);
  });
});

describe('settings', () => {
  test('saves a valid locator normalised and the callsign upper case', () => {
    mountApp(root);
    openSettings();
    const [call, loc] = root.querySelectorAll('input');
    type(call, ' on5gs ');
    type(loc, 'jo21sc');
    const save = root.querySelector('[data-action="save"]');
    expect(save.disabled).toBe(false);
    save.click();
    expect(loadSettings().callsign).toBe('ON5GS');
    expect(loadSettings().locator).toBe('JO21sc');
    expect(tabs()).toHaveLength(5);
  });

  test('refuses XX99 with locator_invalid and does not save', () => {
    mountApp(root);
    openSettings();
    const [call, loc] = root.querySelectorAll('input');
    type(call, 'ON5GS');
    type(loc, 'XX99');
    expect(root.textContent).toContain(t('locator_invalid'));
    const save = root.querySelector('[data-action="save"]');
    expect(save.disabled).toBe(true);
    save.click();
    expect(loadSettings().locator).toBe('');
  });

  test('Save stays disabled with a blank callsign; locator is cut to 6 characters', () => {
    mountApp(root);
    openSettings();
    const [call, loc] = root.querySelectorAll('input');
    type(loc, 'JO21SCXYZ');
    expect(loc.value).toBe('JO21SC');
    type(call, '  ');
    expect(root.querySelector('[data-action="save"]').disabled).toBe(true);
  });

  test('a valid locator shows locator_center with the square centre', () => {
    mountApp(root);
    openSettings();
    type(root.querySelectorAll('input')[1], 'JO21');
    expect(root.textContent).toContain(t('locator_center', '51.5000° N, 5.0000° E'));
  });

  test('language switch re-renders the texts and keeps typed values', () => {
    mountApp(root);
    openSettings();
    type(root.querySelectorAll('input')[0], 'on5gs');
    const select = root.querySelector('select');
    select.value = 'nl';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    expect(root.querySelector('header').textContent).toContain(t('settings'));
    expect(t('settings')).not.toBe('Settings');
    expect(root.textContent).toContain(t('station'));
    expect(root.querySelectorAll('input')[0].value).toBe('ON5GS');
    expect(loadSettings().language).toBe('nl');
    root.querySelector('[data-action="back"]').click();
    expect(tabs()[4].textContent).toBe(t('tab_livecq'));
  });

  test('picker starts with the automatic entry (language_system, value "") followed by the 15 languages', () => {
    mountApp(root);
    openSettings();
    const options = [...root.querySelectorAll('select option')];
    expect(options).toHaveLength(16);
    expect(options[0].value).toBe('');
    expect(options[0].textContent).toBe(t('language_system'));
  });

  test('automatic after nl stores "" and shows the browser language', () => {
    mountApp(root);
    openSettings();
    const pick = (v) => {
      const select = root.querySelector('select');
      select.value = v;
      select.dispatchEvent(new Event('change', { bubbles: true }));
    };
    pick('nl');
    expect(loadSettings().language).toBe('nl');
    pick('');
    expect(loadSettings().language).toBe('');
    expect(root.querySelector('select').value).toBe('');
    expect(root.querySelector('header').textContent).toContain(strings.fr.settings);
    expect(strings.fr.settings).not.toBe(strings.en.settings);
  });

  test('fresh install follows the browser language', () => {
    localStorage.setItem('eme.settings', '{}');
    mountApp(root);
    expect(tabs()[0].textContent).toBe(strings.fr.tab_moon);
  });

  test('the html lang follows the effective language, also after a switch', () => {
    mountApp(root);
    expect(document.documentElement.lang).toBe('en');
    openSettings();
    const select = root.querySelector('select');
    select.value = 'nl';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    expect(document.documentElement.lang).toBe('nl');
    select.value = '';
    select.dispatchEvent(new Event('change', { bubbles: true }));
    expect(document.documentElement.lang).toBe('fr'); // automatic: the browser language (fr-BE)
  });

  test('release notes link opens the tag of this version in a new tab', () => {
    mountApp(root);
    openSettings();
    const a = root.querySelector('a');
    expect(a.getAttribute('href')).toBe(`https://github.com/on5gsdirk-cmd/EME-APP/releases/tag/v${__APP_VERSION__}`);
    expect(a.target).toBe('_blank');
    expect(a.rel).toContain('noopener');
    expect(a.textContent).toBe(`${t('release_notes')}  v${__APP_VERSION__}`);
  });
});
