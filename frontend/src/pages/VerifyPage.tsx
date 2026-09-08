import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { useAuth } from '../auth/auth-context'
import { VERIFICATION_FAILED } from '../auth/problems'

/**
 * The catalog keys this screen can put in front of the user. Held as keys rather than as resolved
 * sentences because the screen carries the language switch: a message stored as text would keep
 * saying whatever it said when it was raised, in a language the user has since left.
 */
type MessageKey =
  | 'verify.errors.code'
  | 'verify.errors.notVerified'
  | 'verify.errors.tooMany'
  | 'verify.errors.unavailable'
  | 'auth.errors.generic'

/** What sent the user here: the address to save them retyping, and the reason where there was one. */
type Handover = { email?: string; error?: MessageKey } | null

/**
 * The address-proof screen. An account exists from the moment sign-up returns, but it is inert —
 * it cannot log in and the natural rhythm never writes to it — until the six digits mailed to the
 * address land here.
 *
 * The address is an ordinary editable field seeded from the route state rather than a line of text:
 * this is the only route to a fresh code, and a reload (or a link followed a day later in another
 * tab) arrives with no state at all. Costing a returning user one retyped address beats a screen
 * that can do nothing for them.
 */
export function VerifyPage() {
  const { t, i18n } = useTranslation()
  const { verify, resendCode } = useAuth()
  const navigate = useNavigate()
  const handover = useLocation().state as Handover
  const [email, setEmail] = useState(handover?.email ?? '')
  const [message, setMessage] = useState<MessageKey | null>(handover?.error ?? null)
  const [sent, setSent] = useState(false)
  const [pending, setPending] = useState(false)

  /** One shape for both actions: clear whatever the last one said, run it, report what this one says. */
  async function run(action: () => Promise<void>, onDone: () => void) {
    setMessage(null)
    setSent(false)
    setPending(true)
    try {
      await action()
      onDone()
    } catch (failure) {
      setMessage(messageFor(failure))
    } finally {
      setPending(false)
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const code = String(new FormData(event.currentTarget).get('code') ?? '')
    // Verifying opens no session, so the next step is the sign-in screen — and `replace`, because
    // a spent code is nothing to come back to with the browser's Back button.
    await run(
      () => verify(email, code),
      () => navigate('/login', { replace: true, state: { verified: true } }),
    )
  }

  return (
    <main className="auth">
      <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
      <h1>{t('verify.heading')}</h1>
      {handover?.email && <p>{t('verify.hint', { email: handover.email })}</p>}
      <form onSubmit={submit}>
        <label>
          {t('auth.email')}
          <input
            name="email"
            type="email"
            required
            maxLength={320}
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          {t('verify.code')}
          {/* Native attributes rather than six boxes wired together: the numeric keypad, the SMS /
              mail autofill and the six-digit rule are all the platform's, and the server checks the
              same pattern anyway. */}
          <input
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{6}"
            maxLength={6}
            required
          />
        </label>
        {message && <p role="alert">{t(message)}</p>}
        {sent && <p role="status">{t('verify.sent')}</p>}
        <button type="submit" disabled={pending}>
          {t('verify.submit')}
        </button>
        {/* Not a submit: a resend must work while the code field is empty, which is the whole
            state a user with no code is in. */}
        <button
          type="button"
          disabled={pending}
          onClick={() => void run(() => resendCode(email), () => setSent(true))}
        >
          {t('verify.sendAgain')}
        </button>
      </form>
    </main>
  )
}

/**
 * Branches on the Problem `type`, never on `detail` — the server tells wrong, expired, spent and
 * unknown apart nowhere, on purpose, so one message covers all four. A 403 without a `type` is a
 * stale CSRF token, which no copy about codes would describe.
 */
function messageFor(failure: unknown): MessageKey {
  if (!(failure instanceof ApiError)) {
    return 'auth.errors.generic'
  }
  if (failure.type === VERIFICATION_FAILED || failure.status === 422) {
    return 'verify.errors.code'
  }
  if (failure.status === 429) {
    // `Retry-After` is not read: the 60 s cooldown dominates the hourly cap, so the wait is the
    // same sentence either way and `ApiError` stays free of header plumbing.
    return 'verify.errors.tooMany'
  }
  if (failure.status === 503) {
    return 'verify.errors.unavailable'
  }
  return 'auth.errors.generic'
}
