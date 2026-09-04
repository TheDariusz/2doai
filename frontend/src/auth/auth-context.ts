import { createContext, use } from 'react'

/**
 * The `User` schema from `openapi.yaml` — identity, plus the language this account reads in.
 *
 * `language` is optional here although the spec makes it required: nothing in the SPA needs it to
 * render, it only seeds i18next at login, and typing it as required would make every fixture that
 * stands in for a session carry a field the screens never read.
 */
export type User = { id: string; email: string; language?: 'PL' | 'EN' }

export type Auth = {
  user: User | null
  status: 'loading' | 'authenticated' | 'anonymous'
  login: (email: string, password: string) => Promise<void>
  /** Registration does not open a session — the server returns 201 and the user then logs in. */
  register: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  deleteAccount: (password: string) => Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const auth = use(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be called inside <AuthProvider>')
  }
  return auth
}
