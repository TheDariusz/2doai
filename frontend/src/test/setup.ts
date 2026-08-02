import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'

// Every mutation goes through the CSRF double-submit, so prime the cookie the server would have
// set. Reset per test, so a test that clears it (to assert the refusal) cannot leak into the next.
beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-123'
})

// Unmount React trees after every test to keep the DOM isolated.
afterEach(() => {
  cleanup()
  document.cookie = 'XSRF-TOKEN=; max-age=0'
})
