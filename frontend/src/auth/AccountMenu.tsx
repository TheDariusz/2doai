import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import type { Language } from '../i18n'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { useAuth } from './auth-context'

/**
 * The Problem `type` the backend puts on a failed re-authentication. `openapi.yaml` is the anchor
 * for this literal, not this file: `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`
 * holds the spec, this line and the server's value together, so a rename on any one side goes red
 * (lessons.md). Nothing else may hardcode it.
 */
const RE_AUTH_FAILED = 'urn:2doai:problem:re-auth-failed'

/** Header controls: the account's language, and the two session-ending actions. */
export function AccountMenu() {
  const { t } = useTranslation()
  const { user, changeLanguage, logout, deleteAccount } = useAuth()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  /**
   * FR-002. The account owns the language and `AuthProvider.changeLanguage` owns the write — what
   * belongs here is what the user is told when it fails.
   */
  async function chooseLanguage(next: Language) {
    setError(null)
    try {
      await changeLanguage(next)
    } catch {
      // The switch reads the live i18next language, so a failed write leaves it where it was.
      setError(t('account.errors.language'))
    }
  }

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
      <span>{user?.email}</span>
      <LanguageSwitch onSelect={chooseLanguage} />
      <button type="button" onClick={onLogout}>
        {t('account.logout')}
      </button>
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
  )
}
