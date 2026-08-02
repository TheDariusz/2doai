import { useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import { AuthContext, type User } from './auth-context'

/**
 * Holds the session for the whole app. `undefined` means "not asked yet" — which is what keeps
 * `ProtectedRoute` from bouncing a logged-in user to `/login` during the first paint.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(undefined)

  // Bootstrap: who am I? A 401 here is the normal anonymous answer, not a failure — and the
  // response still carries the XSRF-TOKEN cookie, so the login POST below can already echo it.
  useEffect(() => {
    api<User>('/users/me').then(setUser, () => setUser(null))
  }, [])

  return (
    <AuthContext
      value={{
        user: user ?? null,
        status: user === undefined ? 'loading' : user ? 'authenticated' : 'anonymous',
        login: async (email, password) => {
          setUser(await api<User>('/sessions', { method: 'POST', body: { email, password } }))
        },
        register: async (email, password) => {
          await api('/users', { method: 'POST', body: { email, password } })
        },
        logout: async () => {
          await api('/sessions/current', { method: 'DELETE' })
          setUser(null)
        },
        deleteAccount: async (password) => {
          await api('/users/me', { method: 'DELETE', body: { password } })
          setUser(null)
        },
      }}
    >
      {children}
    </AuthContext>
  )
}
