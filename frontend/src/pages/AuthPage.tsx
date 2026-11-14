import { useState, type FormEvent } from 'react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, type Path } from 'react-router'
import { ApiError } from '../api/client'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { ThemeSwitch } from '../ThemeSwitch'
import { useAuth } from '../auth/auth-context'
import { RESEND_COOLDOWN_SECONDS, type VerifyHandover } from './verifyHandover'

type Mode = 'login' | 'register'

/**
 * Both entry screens. Signing in asks for the address only and sends the user to the code screen
 * with a code on its way; registering still asks for a password, because the backend requires one
 * until release 2. One component beats two near-copies drifting apart.
 *
 * Like the public landing page, they are rendered before there is an account to read a language
 * from, so they carry the switch that decides what the app is written in until then (FR-001) — the
 * choice is local and survives the walk from one to the other. And because
 * `client.ts` sends whatever is being rendered as `Accept-Language`, the language the sign-up
 * request arrives in is the one the new account starts in (FR-003).
 */
export function AuthPage({ mode }: { mode: Mode }) {
  const { t, i18n } = useTranslation()
  // The two modes are each other's alternative, and `/login` / `/register` are their routes.
  const other = mode === 'login' ? 'register' : 'login'
  const { register, resendCode } = useAuth()
  const navigate = useNavigate()
  // Where a bounced visitor was going: carried on to the code screen, which is where the session
  // now opens and so where the user is finally sent there.
  const state = useLocation().state as { from?: Partial<Path> } | null
  const [error, setError] = useState<string | null>(null)
  // The address a 409 just refused, so the screen can offer the one route that can still help it.
  // Set for every 409, proved or not: the server does not say which, and neither may this.
  const [taken, setTaken] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function toVerify(email: string, handover: VerifyHandover) {
    navigate('/verify', { replace: true, state: { email, from: state?.from, ...handover } })
  }

  /**
   * Sign-in's whole job, and the 409's way out. A 429 still goes on to the code screen: it means a
   * code went out moments ago — possibly the very one in the inbox — and that screen counts down the
   * wait the server named. Every other failure stays here, where the address can be corrected.
   */
  async function requestCode(email: string) {
    try {
      await resendCode(email)
      toVerify(email, { resendAfter: RESEND_COOLDOWN_SECONDS })
    } catch (failure) {
      if (!(failure instanceof ApiError) || failure.status !== 429) {
        throw failure
      }
      toVerify(email, {
        error: 'verify.errors.tooMany',
        resendAfter: failure.retryAfter ?? RESEND_COOLDOWN_SECONDS,
      })
    }
  }

  /** One shape for every action: clear what the last one said, run it, report what this one says. */
  async function run(action: () => Promise<void>, messages: Mode) {
    setError(null)
    setTaken(null)
    setPending(true)
    try {
      await action()
    } catch (failure) {
      setError(messageFor(t, failure, messages))
    } finally {
      setPending(false)
    }
  }

  async function signUp(email: string, password: string) {
    try {
      await register(email, password)
      toVerify(email, { resendAfter: RESEND_COOLDOWN_SECONDS })
    } catch (failure) {
      // A sign-up past the point of no return is not this screen's to report: `register` commits
      // the account and *then* asks for a code, so any failure the server raised after that — the
      // provider refusing (503), a timeout on the code write escaping as a bare 500 — leaves an
      // account that exists and a screen that can do nothing for it. The predicate is the whole 5xx
      // range rather than a list of statuses, because the list is what left the 500 stranding a
      // committed account here. Nothing went out, so the resend is left unlocked.
      if (failure instanceof ApiError && failure.status >= 500) {
        toVerify(email, { error: 'verify.errors.unavailable' })
        return
      }
      if (failure instanceof ApiError && failure.status === 409) {
        setTaken(email)
      }
      throw failure
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const email = String(form.get('email') ?? '')

    if (mode === 'login') {
      await run(() => requestCode(email), 'login')
    } else {
      await run(() => signUp(email, String(form.get('password') ?? '')), 'register')
    }
  }

  return (
    <main className="auth">
      {/* The choice is kept locally (the i18n module persists it), because there is no account to
          store it on yet — and it re-renders both screens in place rather than reloading. */}
      <div className="auth-controls">
        <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
        <ThemeSwitch />
      </div>
      <h1>{t(`auth.${mode}.heading`)}</h1>
      {mode === 'login' && <p>{t('auth.login.hint')}</p>}
      <form onSubmit={submit}>
        <label>
          {t('auth.email')}
          <input name="email" type="email" required maxLength={320} autoComplete="email" />
        </label>
        {mode === 'register' && (
          <label>
            {t('auth.password')}
            <input
              name="password"
              type="password"
              required
              // Mirrors RegisterRequest: min 8. The maximum is BCrypt's 72-byte cap; counting
              // characters only approximates bytes, so it never blocks input the server would have
              // accepted.
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
            />
          </label>
        )}
        {error && <p role="alert">{error}</p>}
        {taken && (
          <p>
            {/* Not a submit: the form still holds the sign-up that was just refused. */}
            <button type="button" disabled={pending} onClick={() => void run(() => requestCode(taken), 'login')}>
              {t('auth.errors.emailTakenSignIn')}
            </button>
          </p>
        )}
        <button type="submit" disabled={pending}>
          {t(`auth.${mode}.submit`)}
        </button>
      </form>
      <p>
        {t(`auth.${mode}.prompt`)} <Link to={`/${other}`}>{t(`auth.${other}.heading`)}</Link>
      </p>
    </main>
  )
}

function messageFor(t: TFunction, failure: unknown, mode: Mode): string {
  const status = failure instanceof ApiError ? failure.status : 0

  if (mode === 'register' && status === 409) {
    return t('auth.errors.emailTaken')
  }
  if (status === 422) {
    // Only RegisterRequest carries @Size(min = 8); a code request carries the address alone, so
    // quoting the rule there would be advice the user cannot act on.
    return mode === 'register' ? t('auth.errors.invalidRegistration') : t('auth.errors.invalidEmail')
  }
  if (status === 503) {
    // For a code request: the send queue is full. The same answer for every address.
    return t('auth.errors.unavailable')
  }
  return t('auth.errors.generic')
}
