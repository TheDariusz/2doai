import { useState, type FormEvent } from 'react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, type Path } from 'react-router'
import { ApiError } from '../api/client'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { useAuth } from '../auth/auth-context'

type Mode = 'login' | 'register'

/**
 * Both credential screens. They differ only in copy, in the password minimum (the server is
 * deliberately looser on login, so as not to leak which submissions could belong to an account)
 * and in where success goes — one component beats two near-copies drifting apart.
 *
 * These are the only screens rendered before there is an account to read a language from, so they
 * carry the switch that decides what the app is written in until then (FR-001) — and, because
 * `client.ts` sends whatever is being rendered as `Accept-Language`, the language the sign-up
 * request arrives in is the one the new account starts in (FR-003).
 */
export function AuthPage({ mode }: { mode: Mode }) {
  const { t, i18n } = useTranslation()
  // The two modes are each other's alternative, and `/login` / `/register` are their routes.
  const other = mode === 'login' ? 'register' : 'login'
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const email = String(form.get('email') ?? '')
    const password = String(form.get('password') ?? '')

    setError(null)
    setPending(true)
    try {
      if (mode === 'login') {
        await login(email, password)
        // The whole location, not just its pathname — a bounced deep link keeps its query and hash.
        const from = (location.state as { from?: Partial<Path> } | null)?.from
        navigate(from ?? '/', { replace: true })
      } else {
        await register(email, password)
        navigate('/login', { replace: true })
      }
    } catch (failure) {
      setError(messageFor(t, failure, mode))
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="auth">
      {/* The choice is kept locally (the i18n module persists it), because there is no account to
          store it on yet — and it re-renders both screens in place rather than reloading. */}
      <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
      <h1>{t(`auth.${mode}.heading`)}</h1>
      <form onSubmit={submit}>
        <label>
          {t('auth.email')}
          <input name="email" type="email" required maxLength={320} autoComplete="email" />
        </label>
        <label>
          {t('auth.password')}
          <input
            name="password"
            type="password"
            required
            // Mirrors RegisterRequest: min 8 on registration, no minimum on login. The maximum is
            // BCrypt's 72-byte cap, which both requests carry; counting characters only
            // approximates bytes, so it never blocks input the server would have accepted.
            minLength={mode === 'register' ? 8 : undefined}
            maxLength={72}
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={pending}>
          {t(`auth.${mode}.heading`)}
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
    // Only RegisterRequest carries @Size(min = 8); LoginRequest is deliberately looser, so quoting
    // the rule there would be advice the user cannot act on.
    return mode === 'register' ? t('auth.errors.invalidRegistration') : t('auth.errors.invalidCredentials')
  }
  if (status === 401) {
    // Identical for an unknown email and a wrong password, exactly as the server answers.
    return t('auth.errors.wrongCredentials')
  }
  if (status === 503) {
    return t('auth.errors.unavailable')
  }
  return t('auth.errors.generic')
}
