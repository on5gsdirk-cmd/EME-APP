import strings from './generated/strings.json';

/** Port of AppLanguage.options (order of the language picker). */
export const LANGUAGES = ['nl', 'fr', 'en', 'de', 'es', 'it', 'pt', 'sv', 'da', 'fi', 'cs', 'ru', 'uk', 'zh', 'ja'];

let current = 'en';

export function setLanguage(code) {
  current = code;
}

/** Android style: %1$s / %2$d are replaced by args[0] / args[1], %% becomes % (only with args, as Android getString); falls back to en, then to the name. */
export function t(name, ...args) {
  const text = strings[current]?.[name] ?? strings.en[name] ?? name;
  if (args.length === 0) return text;
  return text.replace(/%%|%(\d)\$[sd]/g, (m, n) => (m === '%%' ? '%' : args[n - 1] === undefined ? m : String(args[n - 1])));
}
