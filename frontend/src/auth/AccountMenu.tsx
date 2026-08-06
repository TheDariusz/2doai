import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from './auth-context'

/**
 * The Problem `type` the backend puts on a failed re-authentication. `openapi.yaml` is the anchor
 * for this literal, not this file: `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`
 * holds the spec, this line and the server's value together, so a rename on any one side goes red
 * (lessons.md). Nothing else may hardcode it.
 */
const RE_AUTH_FAILED = 'urn:2doai:problem:re-auth-failed'

/** Header controls for the two session-ending actions. */
export function AccountMenu() {
  const { user, logout, deleteAccount } = useAuth()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onLogout() {
    setError(null)
    try {
      await logout()
    } catch {
      // The session may have survived, so say so rather than route to /login as if it had not.
      setError('Nie udało się wylogować. Spróbuj ponownie.')
      return
    }
    navigate('/login', { replace: true })
  }

  async function onDelete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const password = String(new FormData(event.currentTarget).get('password') ?? '')
    setError(null)
    // Irreversible, so a double-click must not become a second DELETE — the first has already
    // taken the account, and the second would 401 and paint an error over a success.
    setPending(true)
    try {
      await deleteAccount(password)
      navigate('/login', { replace: true })
    } catch (failure) {
      // The server types a wrong re-auth password (DEV-31), so this can name it instead of hedging
      // across every 403. Branching on the status alone would put this copy on a CSRF denial too,
      // which has nothing to do with the password the user just typed.
      setError(
        failure instanceof ApiError && failure.type === RE_AUTH_FAILED
          ? 'Nieprawidłowe hasło.'
          : // The other 403 here is a stale CSRF token, which only a reload re-primes — so the
            // fallback names that remedy too, as openapi.yaml's 403 description says it should.
            'Nie udało się usunąć konta. Odśwież stronę i spróbuj ponownie.',
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="account">
      <span>{user?.email}</span>
      <button type="button" onClick={onLogout}>
        Wyloguj
      </button>
      <button type="button" onClick={() => { setError(null); setConfirming(true) }}>
        Usuń konto
      </button>
      {error && <p role="alert">{error}</p>}

      {/* Deletion is irreversible (FR-019), so it is double-gated: this step, then the password
          the server re-verifies. Rendered only while confirming — nothing to mis-click. */}
      {confirming && (
        <form onSubmit={onDelete} className="confirm-delete">
          <p>Usunięcie konta kasuje wszystkie Twoje dane. Tej operacji nie da się cofnąć.</p>
          <label>
            Potwierdź hasłem
            <input name="password" type="password" required autoFocus autoComplete="current-password" />
          </label>
          <button type="submit" disabled={pending}>
            Usuń konto na zawsze
          </button>
          <button type="button" onClick={() => setConfirming(false)}>
            Anuluj
          </button>
        </form>
      )}
    </div>
  )
}
