import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import i18n from '../i18n'

// The real i18n instance and the real catalog, in English — the language most users will see, and
// the one the server falls back to. Not a mock: a key that goes missing has to fail a test the way
// it would fail a user, by rendering the wrong thing rather than the key name.
//
// Every mutation goes through the CSRF double-submit, so the cookie the server would have set is
// primed here too. Both are reset per test, so a test that clears the cookie (to assert the
// refusal) or switches to Polish cannot leak into the next.
beforeEach(async () => {
  document.cookie = 'XSRF-TOKEN=token-123'
  await i18n.changeLanguage('en')
})

// Unmount React trees after every test to keep the DOM isolated.
afterEach(() => {
  cleanup()
  document.cookie = 'XSRF-TOKEN=; max-age=0'
  // The language switch persists the choice; without this, one Polish test would set the language
  // for every test file that runs after it in the same worker.
  localStorage.clear()
})
