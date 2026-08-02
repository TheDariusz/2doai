import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, type Path } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/auth-context'

type Mode = 'login' | 'register'

const COPY = {
  login: {
    heading: 'Zaloguj się',
    submit: 'Zaloguj się',
    prompt: 'Nie masz jeszcze konta?',
    otherLabel: 'Załóż konto',
    otherPath: '/register',
  },
  register: {
    heading: 'Załóż konto',
    submit: 'Załóż konto',
    prompt: 'Masz już konto?',
    otherLabel: 'Zaloguj się',
    otherPath: '/login',
  },
} as const

/**
 * Both credential screens. They differ only in copy, in the password minimum (the server is
 * deliberately looser on login, so as not to leak which submissions could belong to an account)
 * and in where success goes — one component beats two near-copies drifting apart.
 */
export function AuthPage({ mode }: { mode: Mode }) {
  const copy = COPY[mode]
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
      setError(messageFor(failure, mode))
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="auth">
      <h1>{copy.heading}</h1>
      <form onSubmit={submit}>
        <label>
          Email
          <input name="email" type="email" required maxLength={320} autoComplete="email" />
        </label>
        <label>
          Hasło
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
          {copy.submit}
        </button>
      </form>
      <p>
        {copy.prompt} <Link to={copy.otherPath}>{copy.otherLabel}</Link>
      </p>
    </main>
  )
}

function messageFor(failure: unknown, mode: Mode): string {
  const status = failure instanceof ApiError ? failure.status : 0

  if (mode === 'register' && status === 409) {
    return 'Ten adres email jest już zajęty — zaloguj się.'
  }
  if (status === 422) {
    // Only RegisterRequest carries @Size(min = 8); LoginRequest is deliberately looser, so quoting
    // the rule there would be advice the user cannot act on.
    return mode === 'register'
      ? 'Sprawdź adres email i hasło (min. 8 znaków).'
      : 'Sprawdź adres email i hasło.'
  }
  if (status === 401) {
    // Identical for an unknown email and a wrong password, exactly as the server answers.
    return 'Nieprawidłowy email lub hasło.'
  }
  if (status === 503) {
    return 'Logowanie jest chwilowo niedostępne. Spróbuj za moment.'
  }
  return 'Coś poszło nie tak. Spróbuj ponownie.'
}
