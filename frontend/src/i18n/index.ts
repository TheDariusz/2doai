import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { en } from './en'
import { pl } from './pl'

/** The two locales the server renders in, spelled the way BCP 47 and `<html lang>` want them. */
export const LANGUAGES = ['pl', 'en'] as const

export type Language = (typeof LANGUAGES)[number]

/** Where the pre-login choice survives a reload. The account column is the post-login answer. */
const STORED = '2doai.language'

/**
 * Reads a tag as one of the two, or as nothing. Region is ignored: there is nothing to choose
 * between `en` and `en-GB` here. `AppLanguage.of` is the server's half of the same rule.
 */
function supported(tag: string | null): Language | undefined {
  const base = tag?.toLowerCase().split('-')[0]
  return LANGUAGES.find((language) => language === base)
}

/**
 * Local choice first, then the browser, then English — the order FR-001 asks for. Written by hand
 * rather than with `i18next-browser-languagedetector`: `navigator.languages` is already the ordered
 * preference list, so the whole rule is this loop, and a dependency would only wrap it.
 *
 * The *first* supported tag wins rather than "does the list mention Polish": a browser asking for
 * `['en-US', 'pl']` prefers English.
 *
 * Exported for its test: it runs once, during this module's own evaluation, so by the time any test
 * body starts there is nothing left to observe — and `i18next` is a singleton a second `init` will
 * not move, so re-importing this file cannot re-ask it either.
 *
 * Storage is read inside a `try`, and so is the write below. A browser set to block site data does
 * not hand back `null` — every `localStorage` member throws a `SecurityError` (Safari's "Block all
 * cookies", Chrome in a third-party iframe). Both touches happen outside React, one of them while
 * this module is still being evaluated, so an escaping throw is a blank page. Losing the remembered
 * choice is the whole cost of catching it: the browser list still answers, and after login the
 * account column does.
 */
export function detect(): Language {
  try {
    const stored = supported(localStorage.getItem(STORED))
    if (stored) {
      return stored
    }
  } catch {
    // Storage unavailable — fall through to the browser's own preference list.
  }
  for (const tag of navigator.languages ?? [navigator.language]) {
    const match = supported(tag)
    if (match) {
      return match
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
  try {
    localStorage.setItem(STORED, language)
  } catch {
    // Storage unavailable (see `detect`). The switch still applies to this tab; only the next boot
    // forgets it, and the `<html lang>` write above already happened.
  }
})
document.documentElement.lang = i18n.resolvedLanguage ?? 'en'

export default i18n
