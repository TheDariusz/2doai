import { useEffect, useState, type ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import { AuthContext, type User } from './auth-context'

/**
 * Holds the session for the whole app. `undefined` means "not asked yet" — which is what keeps
 * `ProtectedRoute` from bouncing a logged-in user to `/login` during the first paint.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(undefined)

  // Any 401 after the bootstrap means the session ended under the app's feet; `client.ts` raises
  // this event so no caller has to handle it. Registered before the bootstrap effect below so its
  // own 401 cannot be missed.
  useEffect(() => {
    const onExpired = () => setUser(null)
    window.addEventListener('session-expired', onExpired)
    return () => window.removeEventListener('session-expired', onExpired)
  }, [])

  // Bootstrap: who am I? A 401 here is the normal anonymous answer, not a failure — and the
  // response still carries the XSRF-TOKEN cookie, so the login POST below can already echo it.
  // Any other failure is also read as anonymous: the login screen is the one place a user can act
  // from, and stalling on 'loading' would render a permanently blank page.
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
          try {
            await api('/sessions/current', { method: 'DELETE' })
          } catch (failure) {
            // 401 means the session was already gone — that is a logout, not a failure. Anything
            // else and the DELETE never landed, so the server may still hold the session: rethrow
            // rather than clear the state locally and let the user believe they are signed out.
            if (!(failure instanceof ApiError) || failure.status !== 401) {
              throw failure
            }
          }
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
