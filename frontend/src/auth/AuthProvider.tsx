import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { ApiError, api } from '../api/client'
import i18n from '../i18n'
import { AuthContext, type User } from './auth-context'

/**
 * Holds the session for the whole app. `undefined` means "not asked yet" — which is what keeps
 * `ProtectedRoute` from bouncing a logged-in user to `/login` during the first paint.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(undefined)

  /**
   * After login the account decides what the app is rendered in; before it, the browser did
   * (FR-001/FR-002). So every answer that carries a session adopts its language — and only into
   * i18next: reconciling the other way would `PATCH` on every boot, and a write is what wakes the
   * metered database that this app is otherwise careful to let sleep.
   */
  const adopt = useCallback((next: User | null) => {
    const language = next?.language?.toLowerCase()
    // Only when it actually moves: i18next fires `languageChanged` unconditionally, and that is a
    // DOM write, a `localStorage` write and a re-render of every consumer. The common case is a
    // reload where the account and the app already agree, because the same handler persisted it.
    if (language && language !== i18n.resolvedLanguage) {
      void i18n.changeLanguage(language)
    }
    setUser(next)
  }, [])

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
    api<User>('/users/me').then(adopt, () => setUser(null))
  }, [adopt])

  return (
    <AuthContext
      value={{
        user: user ?? null,
        status: user === undefined ? 'loading' : user ? 'authenticated' : 'anonymous',
        login: async (email, password) => {
          adopt(await api<User>('/sessions', { method: 'POST', body: { email, password } }))
        },
        register: async (email, password) => {
          await api('/users', { method: 'POST', body: { email, password } })
        },
        changeLanguage: async (language) => {
          const chosen = language.toUpperCase() as User['language']
          if (chosen === user?.language) {
            // Nothing to store, but the pick is still honoured: if the app and the account ever
            // disagree, this is the one way out of it that costs no write. And the guard is not
            // cosmetic — the server writes unconditionally (it has to: an update guarded on "only
            // if it differs" returns zero rows for a no-op, which it can only read as "no such
            // account" and answer 401), so keeping a same-value switch off the wire is the
            // client's job, and a query is what wakes the metered Neon compute.
            await i18n.changeLanguage(language)
            return
          }
          // `adopt`, not `setUser`: the response is the updated account, so the app follows what
          // the server stored rather than what was asked for, by the same path a login does.
          adopt(await api<User>('/users/me', { method: 'PATCH', body: { language: chosen } }))
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
