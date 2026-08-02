import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from './auth-context'

/** Header controls for the two session-ending actions. */
export function AccountMenu() {
  const { user, logout, deleteAccount } = useAuth()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  async function onDelete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const password = String(new FormData(event.currentTarget).get('password') ?? '')
    setError(null)
    try {
      await deleteAccount(password)
      navigate('/login', { replace: true })
    } catch (failure) {
      // 403 is a mistyped password on a perfectly valid session — not "logged out".
      setError(
        failure instanceof ApiError && failure.status === 403
          ? 'Nieprawidłowe hasło.'
          : 'Nie udało się usunąć konta. Spróbuj ponownie.',
      )
    }
  }

  return (
    <div className="account">
      <span>{user?.email}</span>
      <button type="button" onClick={onLogout}>
        Wyloguj
      </button>
      <button type="button" onClick={() => setConfirming(true)}>
        Usuń konto
      </button>

      {/* Deletion is irreversible (FR-019), so it is double-gated: this step, then the password
          the server re-verifies. Rendered only while confirming — nothing to mis-click. */}
      {confirming && (
        <form onSubmit={onDelete} className="confirm-delete">
          <p>Usunięcie konta kasuje wszystkie Twoje dane. Tej operacji nie da się cofnąć.</p>
          <label>
            Potwierdź hasłem
            <input name="password" type="password" required autoFocus autoComplete="current-password" />
          </label>
          {error && <p role="alert">{error}</p>}
          <button type="submit">Usuń konto na zawsze</button>
          <button type="button" onClick={() => setConfirming(false)}>
            Anuluj
          </button>
        </form>
      )}
    </div>
  )
}
