import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { beforeEach, describe, expect, test } from 'vitest';
import { LANGUAGES, setLanguage, t } from '../src/i18n.js';
import { parseStrings } from '../scripts/strings.mjs';
import strings from '../src/generated/strings.json';

const RES = join(import.meta.dirname, '../../app/src/main/res');
const placeholders = (s) => (s.match(/%\d\$[sd]/g) ?? []).sort();
const untranslatable = parseStrings(readFileSync(join(RES, 'values/strings.xml'), 'utf8'))
  .filter((s) => !s.translatable)
  .map((s) => s.name);
const translatable = (lang) => Object.keys(strings[lang]).filter((k) => !untranslatable.includes(k));

describe('strings.json', () => {
  test('has 15 languages, one per values folder', () => {
    const folders = readdirSync(RES).filter((d) => d.startsWith('values')).length;
    expect(LANGUAGES).toHaveLength(15);
    expect(folders).toBe(15);
    expect(Object.keys(strings).sort()).toEqual([...LANGUAGES].sort());
  });

  test('every language has every key of en with the same placeholders', () => {
    const en = strings.en;
    expect(translatable('en').length).toBeGreaterThan(60);
    for (const lang of LANGUAGES) {
      expect(translatable(lang).sort(), lang).toEqual(translatable('en').sort());
      for (const key of translatable(lang)) {
        expect(placeholders(strings[lang][key]), `${lang}/${key}`).toEqual(placeholders(en[key]));
        expect(strings[lang][key].trim(), `${lang}/${key}`).not.toBe('');
      }
    }
  });

  test('chatcq_header has 7 parts in every language', () => {
    for (const lang of LANGUAGES) {
      expect(strings[lang].chatcq_header.split(' - ')).toHaveLength(7);
    }
  });
});

describe('parseStrings', () => {
  test('handles Android escapes and entities', () => {
    const xml = String.raw`<resources>
      <string name="a">l\'horizon \"x\" a\nb \@c \?d \\e</string>
      <string name="b">&lt;b&gt; &amp; &quot;q&quot; &apos;s&apos; &#65;&#x42;</string>
      <string name="c">"  quoted \' text  "</string>
      <string name="d" translatable="false">x</string>
      <!-- <string name="e">no</string> -->
    </resources>`;
    const r = Object.fromEntries(parseStrings(xml).map((s) => [s.name, s]));
    expect(r.a.text).toBe('l\'horizon "x" a\nb @c ?d ' + String.fromCharCode(92) + 'e');
    expect(r.b.text).toBe('<b> & "q" \'s\' AB');
    expect(r.c.text).toBe("  quoted ' text  ");
    expect(r.d.translatable).toBe(false);
    expect(r.e).toBeUndefined();
  });
});

describe('t', () => {
  beforeEach(() => setLanguage('en'));

  test('nl chatcq_title fills the placeholders', () => {
    setLanguage('nl');
    expect(t('chatcq_title', '144', 3)).toBe('CQ-aanroepen in de HB9Q-chat (144), laatste 60 minuten: 3');
  });

  test('%% becomes % when args are given (moon_phase)', () => {
    expect(t('moon_phase', 'Full', 75)).toBe('Moon phase: Full (75% lit)');
    setLanguage('nl');
    expect(t('moon_phase', 'Vol', 75)).toBe('Maanfase: Vol (75% verlicht)');
  });

  test('%% stays when no args are given, as Android', () => {
    expect(t('moon_phase')).toContain('%2$d%% lit');
  });

  test('unescaped apostrophe', () => {
    setLanguage('fr');
    expect(t('moon_down')).toBe("lune ↓ sous l'horizon");
  });

  test('unknown language falls back to en, unknown key returns the name', () => {
    setLanguage('xx');
    expect(t('moon_down')).toBe(strings.en.moon_down);
    expect(t('no_such_key')).toBe('no_such_key');
  });
});

describe('__APP_VERSION__', () => {
  test('equals versionName in app/build.gradle.kts', () => {
    const gradle = readFileSync(join(import.meta.dirname, '../../app/build.gradle.kts'), 'utf8');
    expect(__APP_VERSION__).toBe(/versionName\s*=\s*"([^"]+)"/.exec(gradle)[1]);
    expect(__APP_VERSION__).toMatch(/^\d+\.\d+\.\d+/);
  });
});
