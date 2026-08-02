import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthContext, type Auth } from '../auth/auth-context'

/** An auth context with stubbed actions, so a screen can be tested without a provider or fetch. */
export function stubAuth(overrides: Partial<Auth> = {}): Auth {
  return {
    user: null,
    status: 'anonymous',
    login: vi.fn().mockResolvedValue(undefined),
    register: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
    deleteAccount: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

/** The signed-in overrides every shell test starts from. */
export const LOGGED_IN: Partial<Auth> = {
  status: 'authenticated',
  user: { id: 'u1', email: 'ala@example.pl' },
}

/** Minimal stand-in for `Response` — the client only reads these four members. */
export function response(status: number, body?: unknown) {
  return { ok: status < 400, status, statusText: '', json: async () => body }
}

/** Mounts a screen at `path` with `auth` in context — the shape every screen test needs. */
export function renderWithAuth(
  ui: ReactNode,
  { path = '/', auth = stubAuth() }: { path?: string; auth?: Auth } = {},
) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext value={auth}>{ui}</AuthContext>
    </MemoryRouter>,
  )
}
