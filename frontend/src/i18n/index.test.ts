import { beforeEach, expect, it, vi } from 'vitest'
import i18n, { detect } from './index'

/** The key the module reads its stored choice from; private there, so restated here. */
const STORED = '2doai.language'

/**
 * No stored choice unless a test plants one. `src/test/setup.ts` puts every test in English, and
 * that switch persists through the same handler a real one does — so without this, every case below
 * would be answered by storage before the browser was ever asked.
 */
beforeEach(() => {
  localStorage.clear()
})

function browser(languages: Partial<Navigator>) {
  vi.stubGlobal('navigator', languages)
}

/**
 * FR-001's browser half. Two rules that are easy to write the other way round by accident: the
 * *first* supported tag wins rather than "the list mentions Polish", and a region is not a choice —
 * there is nothing to pick between `en` and `en-GB`.
 */
it.each<[string[], string]>([
  [['en-US', 'pl'], 'en'],
  [['pl-PL', 'en'], 'pl'],
  [['de-DE', 'fr', 'pl'], 'pl'],
  [['en-GB'], 'en'],
  // Neither is on offer, and English is what the server falls back to as well.
  [['de-DE'], 'en'],
  [[], 'en'],
])('opens a browser asking for %s in %s', (languages, expected) => {
  browser({ languages })

  expect(detect()).toBe(expected)
})

it('reads the single-language property when the browser offers no list', () => {
  browser({ languages: undefined, language: 'pl' })

  expect(detect()).toBe('pl')
})

it('prefers the stored choice over everything the browser asks for', () => {
  browser({ languages: ['en-US', 'en'] })
  localStorage.setItem(STORED, 'pl')

  expect(detect()).toBe('pl')
})

/** A value left by an older release, or edited by hand. It is a preference, not a promise. */
it('ignores a stored value that is not one of the two', () => {
  browser({ languages: ['pl-PL'] })
  localStorage.setItem(STORED, 'de')

  expect(detect()).toBe('pl')
})

/**
 * A browser set to block site data does not return `null` from `localStorage` — every member throws
 * a `SecurityError` (Safari's "Block all cookies", Chrome inside a third-party iframe). Both touches
 * happen outside React, and the read happens while this module is still being evaluated, so an
 * escaping throw is a blank page rather than a forgotten preference.
 */
const blocked: Storage = {
  length: 0,
  clear: () => {},
  key: () => null,
  removeItem: () => {},
  getItem() {
    throw new DOMException('The operation is insecure.', 'SecurityError')
  },
  setItem() {
    throw new DOMException('The operation is insecure.', 'SecurityError')
  },
}

it('still asks the browser when the stored choice cannot be read', () => {
  vi.stubGlobal('localStorage', blocked)
  browser({ languages: ['pl-PL'] })

  expect(detect()).toBe('pl')
})

it('switches, and relabels the document, when the choice cannot be written', async () => {
  vi.stubGlobal('localStorage', blocked)

  await i18n.changeLanguage('pl')

  expect(i18n.resolvedLanguage).toBe('pl')
  expect(document.documentElement.lang).toBe('pl')
})
