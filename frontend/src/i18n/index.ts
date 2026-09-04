import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { en } from './en'
import { pl } from './pl'

/** The two locales the server renders in, spelled the way BCP 47 and `<html lang>` want them. */
export type Language = 'pl' | 'en'

/** Where the pre-login choice survives a reload. The account column is the post-login answer. */
const STORED = '2doai.language'

/**
 * Local choice first, then the browser, then English — the order FR-001 asks for. Written by hand
 * rather than with `i18next-browser-languagedetector`: `navigator.languages` is already the ordered
 * preference list, so the whole rule is this loop, and a dependency would only wrap it.
 *
 * The *first* supported tag wins rather than "does the list mention Polish": a browser asking for
 * `['en-US', 'pl']` prefers English, and region is ignored because there is nothing to choose
 * between `en` and `en-GB` here.
 */
function detect(): Language {
  const stored = localStorage.getItem(STORED)
  if (stored === 'pl' || stored === 'en') {
    return stored
  }
  for (const tag of navigator.languages ?? [navigator.language]) {
    const base = tag.toLowerCase().split('-')[0]
    if (base === 'pl' || base === 'en') {
      return base
    }
  }
  return 'en'
}

i18n.use(initReactI18next).init({
  resources: { pl: { translation: pl }, en: { translation: en } },
  lng: detect(),
  fallbackLng: 'en',
  // React escapes everything it renders already; escaping here would double-encode an apostrophe.
  interpolation: { escapeValue: false },
})

/**
 * The document's language is not decoration: it drives screen-reader pronunciation and hyphenation
 * (browsers format `<input type="date">` from their own locale, not from this). `index.html` carries
 * the initial value; from here on the switch owns it. The same handler persists the choice, which is what makes it survive a reload — and,
 * after login, what carries the account's language into the next boot before `/users/me` lands.
 */
i18n.on('languageChanged', (language) => {
  document.documentElement.lang = language
  localStorage.setItem(STORED, language)
})
document.documentElement.lang = i18n.resolvedLanguage ?? 'en'

export default i18n
