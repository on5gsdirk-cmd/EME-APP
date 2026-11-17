import { clearCredentials, loadCredentials, saveCredentials } from '../credentials.js';
import { hb9qStore } from '../hb9q-store.js';
import { LANGUAGES, t } from '../i18n.js';
import { isValid, normalize, toLatLon } from '../lib/maidenhead.js';
import { saveSettings } from '../settings.js';
import { horizonEditor, horizonSection } from './horizon.js';
import { defaultHorizon } from '../lib/horizon.js';

/** Port of AppLanguage.options (native names, as in the language picker of the app). */
const LANGUAGE_NAMES = {
  nl: 'Nederlands', fr: 'Français', en: 'English', de: 'Deutsch', es: 'Español', it: 'Italiano', pt: 'Português',
  sv: 'Svenska', da: 'Dansk', fi: 'Suomi', cs: 'Čeština', ru: 'Русский', uk: 'Українська', zh: '中文', ja: '日本語',
};

const RELEASE_NOTES_URL = `https://github.com/on5gsdirk-cmd/EME-APP/releases/tag/v${__APP_VERSION__}`;

function el(tag, props = {}, ...children) {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...children);
  return node;
}

const formatCentre = ({ latDeg, lonDeg }) =>
  `${Math.abs(latDeg).toFixed(4)}° ${latDeg >= 0 ? 'N' : 'S'}, ${Math.abs(lonDeg).toFixed(4)}° ${lonDeg >= 0 ? 'E' : 'W'}`;

/**
 * Settings screen (SettingsScreen.kt, with the HB9Q login).
 * ctx.draft ({ callsign, locator }) lives in the caller so typed values survive a re-render after a language switch.
 * The screen calls ctx.onLanguage(code) and ctx.onSaved().
 */
export function createSettingsScreen() {
  // "My horizon" is edited inside the settings (as SettingsScreen.kt); back returns to the settings.
  let editingHorizon = false;
  let horizonSaved = false;
  const screen = {
    mount(host, ctx) {
      const { settings, draft } = ctx;
      host.replaceChildren();
      const horizon = settings.horizon ?? defaultHorizon();

      if (editingHorizon) {
        const ok = isValid(draft.locator);
        host.append(
          horizonEditor({
            saved: horizon,
            locator: ok ? draft.locator : settings.locator,
            nowMillis: (ctx.now ?? Date.now)(),
            savedDishM: settings.dishDiameterM ?? 0,
            savedDishBand: settings.dishBand ?? 'B1296',
            onSaved: (h, dish, band) => {
              saveSettings({ horizon: h, dishDiameterM: dish, dishBand: band });
              settings.horizon = h;
              settings.dishDiameterM = dish;
              settings.dishBand = band;
              horizonSaved = true;
              editingHorizon = false;
              screen.mount(host, ctx);
            },
            onBack: () => {
              editingHorizon = false;
              screen.mount(host, ctx);
            },
          }),
        );
        return;
      }

      const locatorValid = () => isValid(draft.locator);
      const saveEnabled = () => locatorValid() && draft.callsign.trim() !== '';

      const select = el(
        'select',
        {},
        el('option', { value: '', textContent: t('language_system') }),
        ...LANGUAGES.map((c) => el('option', { value: c, textContent: LANGUAGE_NAMES[c] })),
      );
      select.value = settings.language;
      select.addEventListener('change', () => ctx.onLanguage(select.value));

      const callsign = el('input', { type: 'text', value: draft.callsign, autocapitalize: 'characters', spellcheck: false });
      const locator = el('input', { type: 'text', value: draft.locator, autocapitalize: 'characters', spellcheck: false });
      const support = el('div', { className: 'support' });
      const locatorField = el('label', { className: 'field' }, t('locator_label'), locator, support);
      const save = el('button', { type: 'button', className: 'button', textContent: t('save') });
      save.dataset.action = 'save';

      const refresh = () => {
        const valid = locatorValid();
        locatorField.classList.toggle('invalid', !valid);
        const pos = toLatLon(draft.locator);
        support.textContent = !valid ? t('locator_invalid') : pos ? t('locator_center', formatCentre(pos)) : '';
        save.disabled = !saveEnabled();
      };

      callsign.addEventListener('input', () => {
        draft.callsign = callsign.value.toUpperCase().trim();
        callsign.value = draft.callsign;
        refresh();
      });
      locator.addEventListener('input', () => {
        draft.locator = locator.value.trim().slice(0, 6);
        locator.value = draft.locator;
        refresh();
      });
      save.addEventListener('click', () => {
        if (!saveEnabled()) return;
        saveSettings({ callsign: draft.callsign, locator: normalize(draft.locator) });
        ctx.onSaved();
      });

      const saved = loadCredentials();
      let hasSaved = saved !== null;
      let login = saved?.login ?? '';
      let password = '';
      const roleOf = (node, role) => {
        node.dataset.role = role;
        return node;
      };
      const loginInput = roleOf(
        el('input', { type: 'text', value: login, autocomplete: 'username', autocapitalize: 'characters', spellcheck: false }),
        'hb9q-login',
      );
      const passwordInput = roleOf(
        el('input', { type: 'password', value: '', autocomplete: 'current-password', spellcheck: false }),
        'hb9q-password',
      );
      const passwordLabelText = el('span');
      const passwordLabel = roleOf(el('label', { className: 'field' }, passwordLabelText, passwordInput), 'hb9q-password-label');
      const toggle = roleOf(el('button', { type: 'button', className: 'text-button' }), 'toggle-password');
      const saveLogin = roleOf(el('button', { type: 'button', className: 'button', textContent: t('save_login') }), 'save-login');
      const clear = roleOf(el('button', { type: 'button', className: 'button outlined', textContent: t('clear') }), 'clear-login');
      const buttonRow = el('div', { className: 'button-row' }, saveLogin);
      const message = roleOf(el('div', { className: 'message' }), 'login-message');

      const refreshLogin = () => {
        passwordLabelText.textContent = hasSaved ? t('password_saved') : t('password');
        toggle.textContent = passwordInput.type === 'password' ? t('show') : t('hide');
        saveLogin.disabled = !(login !== '' && (password !== '' || hasSaved));
        if (hasSaved !== (clear.parentNode !== null)) {
          if (hasSaved) buttonRow.append(clear);
          else clear.remove();
        }
      };
      loginInput.addEventListener('input', () => {
        login = loginInput.value.toUpperCase().trim();
        loginInput.value = login;
        refreshLogin();
      });
      passwordInput.addEventListener('input', () => {
        password = passwordInput.value;
        refreshLogin();
      });
      toggle.addEventListener('click', () => {
        passwordInput.type = passwordInput.type === 'password' ? 'text' : 'password';
        refreshLogin();
      });
      saveLogin.addEventListener('click', () => {
        if (saveLogin.disabled) return;
        // an empty password field keeps the saved one (SavedPassword.kt)
        saveCredentials(login, password !== '' ? password : (loadCredentials()?.password ?? ''));
        hasSaved = true;
        password = '';
        passwordInput.value = '';
        message.textContent = t('login_saved');
        refreshLogin();
      });
      clear.addEventListener('click', () => {
        clearCredentials();
        hb9qStore.forgetServerSession();
        hasSaved = false;
        login = '';
        password = '';
        loginInput.value = '';
        passwordInput.value = '';
        message.textContent = t('login_cleared');
        refreshLogin();
      });

      const notes = el('a', {
        className: 'link',
        href: RELEASE_NOTES_URL,
        target: '_blank',
        rel: 'noopener noreferrer',
        textContent: `${t('release_notes')}  v${__APP_VERSION__}`,
      });

      host.append(
        el('div', { className: 'settings' },
          el('h2', { textContent: t('language') }), select,
          el('hr'),
          el('h2', { textContent: t('station') }),
          el('label', { className: 'field' }, t('callsign'), callsign),
          locatorField, save,
          el('hr'),
          ...horizonSection(horizon, () => {
            horizonSaved = false;
            editingHorizon = true;
            screen.mount(host, ctx);
          }),
          ...(horizonSaved ? [el('div', { className: 'message', textContent: t('horizon_saved') })] : []),
          el('hr'),
          el('h2', { textContent: t('hb9q_chat') }),
          el('div', { className: 'support', textContent: t('login_stored_browser') }),
          el('label', { className: 'field' }, t('hb9q_login'), loginInput),
          el('div', { className: 'password-row' }, passwordLabel, toggle),
          buttonRow,
          message,
          el('hr'), notes),
      );
      refresh();
      refreshLogin();
    },
    unmount() {},
  };
  return screen;
}
