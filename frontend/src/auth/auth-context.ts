import { createContext, use } from 'react'

/**
 * The `User` schema from `openapi.yaml` — identity, plus the language this account reads in.
 *
 * `language` is required, as the spec makes it: the account menu's "skip the PATCH when nothing
 * would change" guard compares against it, and an absent value would turn every pick into a write.
 * The two literals are copied from the spec; `AuthApiTest` holds this file to `AppLanguage`.
 */
export type User = { id: string; email: string; language: 'PL' | 'EN' }

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
