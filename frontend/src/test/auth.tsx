import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, type InitialEntry } from 'react-router'
import { vi } from 'vitest'
import { AuthContext, type Auth } from '../auth/auth-context'

/** An auth context with stubbed actions, so a screen can be tested without a provider or fetch. */
export function stubAuth(overrides: Partial<Auth> = {}): Auth {
  return {
    user: null,
    status: 'anonymous',
    login: vi.fn().mockResolvedValue(undefined),
    register: vi.fn().mockResolvedValue(undefined),
    verify: vi.fn().mockResolvedValue(undefined),
    resendCode: vi.fn().mockResolvedValue(undefined),
    changeLanguage: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    deleteAccount: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

/** The signed-in overrides every shell test starts from. */
export const LOGGED_IN: Partial<Auth> = {
  status: 'authenticated',
  user: { id: 'u1', email: 'ala@example.pl', language: 'EN' },
}

/**
 * Minimal stand-in for `Response`, honest about the one thing that used to hide a bug: an answer
 * with no body has an empty `text()` and a `json()` that throws `SyntaxError`, exactly as the
 * platform does — a stub that resolved to `undefined` there let a broken 202 path pass.
 */
export function response(status: number, body?: unknown) {
  const text = body === undefined ? '' : JSON.stringify(body)
  return {
    ok: status < 400,
    status,
    statusText: '',
    text: async () => text,
    json: async () => JSON.parse(text) as unknown,
  }
}

/** Mounts a screen at `path` with `auth` in context — the shape every screen test needs. */
export function renderWithAuth(
  ui: ReactNode,
  { path = '/', auth = stubAuth() }: { path?: InitialEntry; auth?: Auth } = {},
) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext value={auth}>{ui}</AuthContext>
    </MemoryRouter>,
  )
}
