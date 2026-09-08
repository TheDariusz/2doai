import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthPage } from './AuthPage'
import { VerifyPage } from './VerifyPage'
import { ApiError } from '../api/client'
import { EMAIL_NOT_VERIFIED } from '../auth/problems'
import { renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from '../auth/auth-context'

function renderAt(path: '/login' | '/register', auth: Auth) {
  renderWithAuth(
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="/verify" element={<VerifyPage />} />
      <Route path="/" element={<p>the app</p>} />
    </Routes>,
    { path, auth },
  )
}

async function fillIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: /sign in|create account/i }))
}

describe('AuthPage — the language switch (FR-001)', () => {
  /**
   * The auth screens are the only ones rendered before there is an account to read a language
   * from, so the switch on them is the whole of FR-001. It re-renders in place rather than
   * reloading (FR-010), which is why the half-filled form has to survive it.
   */
  it('re-renders both screens in the language picked, without losing what was typed', async () => {
    renderAt('/login', stubAuth())
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'ala@example.pl')
    // Named by the endonym, which is the whole point of one: it is the same word in both
    // languages, so the switch is findable from the language you cannot read.
    await user.click(screen.getByRole('button', { name: 'Polski' }))

    expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Załóż konto' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('ala@example.pl')
    // The control keeps saying which language is on — both halves stay on screen, one pressed.
    expect(screen.getByRole('group', { name: 'Język' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Polski' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'English' })).toHaveAttribute('aria-pressed', 'false')
    // Kept locally, because there is no account to keep it on yet: it is what the sign-up request
    // then carries as Accept-Language, and what the account inherits from it (FR-003).
    expect(localStorage.getItem('2doai.language')).toBe('pl')
  })
})

describe('AuthPage — signing in', () => {
  it('submits the credentials and lands in the app', async () => {
    const auth = stubAuth()
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'tajnehaslo')

    expect(auth.login).toHaveBeenCalledWith('ala@example.pl', 'tajnehaslo')
    expect(await screen.findByText('the app')).toBeInTheDocument()
  })

  it('shows a generic message on 401 — never which half was wrong', async () => {
    const auth = stubAuth({ login: async () => { throw new ApiError(401, 'Authentication is required or credentials are invalid') } })
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'zlehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.')
  })

  /**
   * The password was right; only the address was never confirmed. Reporting that as a credentials
   * failure would send the user back to a form that can never succeed.
   */
  it('sends an unconfirmed account to the code screen instead of reporting bad credentials', async () => {
    const auth = stubAuth({
      login: async () => { throw new ApiError(403, 'Email not verified', EMAIL_NOT_VERIFIED) },
    })
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('heading', { name: 'Confirm your address' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/confirm your address first/i)
    expect(screen.getByLabelText('Email')).toHaveValue('ala@example.pl')
  })

  it('does not quote the registration password rule when login is rejected (422)', async () => {
    const auth = stubAuth({ login: async () => { throw new ApiError(422, 'Validation failed') } })
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'x')

    // LoginRequest has no minimum — advice the user cannot act on is worse than none.
    expect(await screen.findByRole('alert')).not.toHaveTextContent(/8 characters/)
  })
})

describe('AuthPage — registering', () => {
  it('registers and sends the user on to the code the server just mailed', async () => {
    const auth = stubAuth()
    renderAt('/register', auth)

    await fillIn('nowa@example.pl', 'tajnehaslo')

    expect(auth.register).toHaveBeenCalledWith('nowa@example.pl', 'tajnehaslo')
    expect(await screen.findByRole('heading', { name: 'Confirm your address' })).toBeInTheDocument()
    // Carried over, so the address only has to be typed once — and the resend has something to send to.
    expect(screen.getByLabelText('Email')).toHaveValue('nowa@example.pl')
  })

  /**
   * The account exists, only the code never left — so the remedy is the resend on the next screen,
   * not a retry of a sign-up that would now answer 409.
   */
  it('still goes to the code screen when the code could not be mailed (503)', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(503, 'Mail provider refused') } })
    renderAt('/register', auth)

    await fillIn('nowa@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('heading', { name: 'Confirm your address' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be sent/i)
  })

  /**
   * The 503's twin, and the reason the handover keys on the shape of the failure rather than on a
   * list of statuses: sign-up commits the account before it asks for a code, so *any* server-side
   * failure after that point — a database timeout on the code write escaping as a bare 500 —
   * leaves the same account-exists-no-code-arrived state that only the code screen can act on.
   */
  it('still goes to the code screen when the code write failed outright (500)', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(500, 'Internal Server Error') } })
    renderAt('/register', auth)

    await fillIn('nowa@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('heading', { name: 'Confirm your address' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be sent/i)
  })

  /** Login is not that: it commits nothing, so its failures belong on the screen that raised them. */
  it('keeps a failed sign-in on the sign-in screen', async () => {
    const auth = stubAuth({ login: async () => { throw new ApiError(429, 'Slow down') } })
    renderAt('/login', auth)

    await fillIn('nowa@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument()
  })

  it('maps 409 to "email already in use, log in instead"', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } })
    renderAt('/register', auth)

    await fillIn('zajety@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)
  })

  /**
   * A 409 on an address whose code never arrived used to be a dead end: "sign in instead" only
   * reaches /verify with the password the *first* sign-up used, and any other one is a plain 401.
   * The link is offered on every 409, proved or not — the server does not say which, and a link
   * that appeared only for unproved accounts would say it for them.
   */
  it('offers the verify screen after a 409, carrying the address', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } })
    renderAt('/register', auth)

    await fillIn('zajety@example.pl', 'tajnehaslo')

    const user = userEvent.setup()
    await user.click(await screen.findByRole('link', { name: /confirm your address/i }))

    expect(await screen.findByRole('heading', { name: /confirm/i })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('zajety@example.pl')
  })

  it('mirrors the server contract client-side: email format and an 8-character minimum', () => {
    renderAt('/register', stubAuth())

    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText('Password')).toHaveAttribute('minlength', '8')
  })
})
