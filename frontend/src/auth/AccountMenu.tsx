import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
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

/**
 * Ending the session, as its own header control. Split from the menu below because the two sit side
 * by side in the header rather than one inside the other — and because a failure here must be
 * readable without opening anything, which is why it carries its own message.
 */
export function LogoutButton() {
  const { t } = useTranslation()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  async function onLogout() {
    setError(null)
    try {
      await logout()
    } catch {
      // The session may have survived, so say so rather than route to /login as if it had not.
      setError(t('account.errors.logout'))
      return
    }
    navigate('/login', { replace: true })
  }

  return (
    <>
      <button type="button" onClick={onLogout}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="M16 17l5-5-5-5" />
          <path d="M21 12H9" />
        </svg>
        <span className="logout-text">{t('account.logout')}</span>
      </button>
      {error && <p role="alert">{error}</p>}
    </>
  )
}

/**
 * The account, as a chip that opens onto what can be done to it. Deletion is the only entry today,
 * and it is the one action on the screen that cannot be undone — a step behind a closed menu is a
 * step it cannot be reached by mistake.
 */
export function AccountMenu() {
  const { t } = useTranslation()
  const { user, deleteAccount } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

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
          ? t('account.errors.wrongPassword')
          : // The other 403 here is a stale CSRF token, which only a reload re-primes — so the
            // fallback names that remedy too, as openapi.yaml's 403 description says it should.
            t('account.errors.delete'),
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="account">
      <button
        type="button"
        className="account-chip"
        aria-expanded={open}
        // Closing drops the confirmation with the panel: reopening must land on the menu, never
        // part-way into the one action that cannot be undone.
        onClick={() => { setOpen(!open); setConfirming(false); setError(null) }}
      >
        {/* Decoration, not identity: the email beside it is the accessible name of the chip. */}
        <span className="avatar" aria-hidden="true">{user?.email?.[0]?.toUpperCase()}</span>
        {/* Wrapped so a phone-width header can drop the text off-screen and keep the name. */}
        <span className="chip-email">{user?.email}</span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>

      {open && (
        <div className="account-panel">
          <p className="account-email">{user?.email}</p>
          <button type="button" onClick={() => { setError(null); setConfirming(true) }}>
            {t('account.delete')}
          </button>
          {error && <p role="alert">{error}</p>}

          {/* Deletion is irreversible (FR-019), so it is double-gated: this step, then the password
              the server re-verifies. Rendered only while confirming — nothing to mis-click. */}
          {confirming && (
            <form onSubmit={onDelete} className="confirm-delete">
              <p>{t('account.deleteWarning')}</p>
              <label>
                {t('account.confirmPassword')}
                <input name="password" type="password" required autoFocus autoComplete="current-password" />
              </label>
              <button type="submit" disabled={pending}>
                {t('account.deleteForever')}
              </button>
              <button type="button" onClick={() => setConfirming(false)}>
                {t('account.cancel')}
              </button>
            </form>
          )}
        </div>
      )}
    </div>
  )
}
