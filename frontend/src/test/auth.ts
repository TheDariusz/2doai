import { vi } from 'vitest'
import type { Auth } from '../auth/auth-context'

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
