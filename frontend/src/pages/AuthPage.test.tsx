import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation, type InitialEntry } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthPage } from './AuthPage'
import { VerifyPage } from './VerifyPage'
import { ApiError } from '../api/client'
import { LOGGED_IN, renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from '../auth/auth-context'

/** Prints what the code screen was handed, so the handover itself can be asserted. */
function HandoverProbe() {
  return <p data-testid="handover">{JSON.stringify(useLocation().state)}</p>
}

function renderAt(path: InitialEntry, auth: Auth, verify = <VerifyPage />) {
  renderWithAuth(
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="/verify" element={verify} />
      <Route path="/goals" element={<p>the app</p>} />
      <Route path="/domain/:code" element={<p>the domain</p>} />
    </Routes>,
    { path, auth },
  )
}

async function requestCode(email: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.click(screen.getByRole('button', { name: 'Email me a code' }))
}

async function signUp(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Create account' }))
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
    expect(screen.getByRole('group', { name: 'Motyw' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Polski' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'English' })).toHaveAttribute('aria-pressed', 'false')
    // Kept locally, because there is no account to keep it on yet: it is what the sign-up request
    // then carries as Accept-Language, and what the account inherits from it (FR-003).
    expect(localStorage.getItem('2doai.language')).toBe('pl')
  })
})

describe('AuthPage — signing in', () => {
  /**
   * The code is the only way in on the screen. The backend still accepts a password until release
   * 2, but the one account that needs this change cannot use its own — and a form built now would
   * be deleted again with the password.
   */
  it('asks for the address only — no password field on /login, while /register keeps one', () => {
    renderAt('/login', stubAuth())

    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
    expect(document.querySelector('input[type="password"]')).toBeNull()
  })

  it('requests a code and lands on the code screen, carrying the address and where the user was going', async () => {
    const auth = stubAuth()
    const from = { pathname: '/domain/leisure', search: '?view=week', hash: '' }
    renderAt({ pathname: '/login', state: { from } }, auth, <HandoverProbe />)

    await requestCode('ala@example.pl')

    expect(auth.resendCode).toHaveBeenCalledWith('ala@example.pl')
    const handover = JSON.parse(screen.getByTestId('handover').textContent ?? 'null')
    expect(handover).toMatchObject({ email: 'ala@example.pl', from })
  })

  it('locks the resend on the code screen, because a code has just been sent', async () => {
    renderAt('/login', stubAuth())

    await requestCode('ala@example.pl')

    expect(await screen.findByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('ala@example.pl')
    expect(screen.getByRole('button', { name: 'Send the code again' })).toBeDisabled()
  })

  /**
   * A 429 means a code went out moments ago — possibly the one already in the inbox — so the screen
   * that takes it is still where this user belongs, with the wait the server named.
   */
  it('still goes to the code screen when the request is throttled, counting down the server\'s wait', async () => {
    const auth = stubAuth({ resendCode: async () => { throw new ApiError(429, 'Slow down', undefined, 42) } })
    renderAt('/login', auth)

    await requestCode('ala@example.pl')

    expect(await screen.findByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/too many code requests/i)
    expect(screen.getByText(/another code in 0:42/)).toBeInTheDocument()
  })

  it('stays on the sign-in screen when no code can be sent today (503)', async () => {
    const auth = stubAuth({ resendCode: async () => { throw new ApiError(503, 'Auth mail unavailable') } })
    renderAt('/login', auth)

    await requestCode('ala@example.pl')

    expect(await screen.findByRole('alert')).toHaveTextContent(/unavailable right now/i)
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('does not quote the registration password rule when the address is rejected (422)', async () => {
    const auth = stubAuth({ resendCode: async () => { throw new ApiError(422, 'Validation failed') } })
    renderAt('/login', auth)

    await requestCode('ala@example.pl')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Check the email address.')
    expect(alert).not.toHaveTextContent(/8 characters/)
  })
})

describe('AuthPage — registering', () => {
  it('registers and sends the user on to the code the server just mailed', async () => {
    const auth = stubAuth()
    renderAt('/register', auth)

    await signUp('nowa@example.pl', 'tajnehaslo')

    expect(auth.register).toHaveBeenCalledWith('nowa@example.pl', 'tajnehaslo')
    expect(await screen.findByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
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

    await signUp('nowa@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be sent/i)
    // Nothing went out, so nothing is waiting to be throttled.
    expect(screen.getByRole('button', { name: 'Send the code again' })).toBeEnabled()
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

    await signUp('nowa@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be sent/i)
  })

  it('maps 409 to "email already registered"', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } })
    renderAt('/register', auth)

    await signUp('zajety@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)
  })

  /**
   * A taken address is one a code can sign in, proved or not — the first code also proves it. The
   * offer is made on every 409: the server does not say which, and an offer that appeared only for
   * unproved accounts would say it for them.
   */
  it('offers a sign-in code after a 409, for the address that was refused', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } })
    renderAt('/register', auth)

    await signUp('zajety@example.pl', 'tajnehaslo')
    await userEvent.setup().click(await screen.findByRole('button', { name: /sign-in code instead/i }))

    expect(auth.resendCode).toHaveBeenCalledWith('zajety@example.pl')
    expect(await screen.findByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('zajety@example.pl')
  })

  it('mirrors the server contract client-side: email format and an 8-character minimum', () => {
    renderAt('/register', stubAuth())

    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText('Password')).toHaveAttribute('minlength', '8')
  })
})

describe('AuthPage — a browser that is already signed in (DEV-64)', () => {
  /**
   * A session the bootstrap found — a live one, or one the remember-me cookie brought back — needs
   * no code. Rendering the form anyway mailed one, and the code screen then forwarded the session
   * on, which read as a sign-in that skipped the code.
   */
  it.each(['/login', '/register'])('sends a signed-in user from %s into the app, with no form', (path) => {
    const auth = stubAuth(LOGGED_IN)
    renderAt(path, auth)

    expect(screen.getByText('the app')).toBeInTheDocument()
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
    expect(auth.resendCode).not.toHaveBeenCalled()
    expect(auth.register).not.toHaveBeenCalled()
  })

  it('goes on to where the user was going, when a guard bounced them here', () => {
    const from = { pathname: '/domain/leisure', search: '', hash: '' }
    renderAt({ pathname: '/login', state: { from } }, stubAuth(LOGGED_IN))

    expect(screen.getByText('the domain')).toBeInTheDocument()
  })

  it('keeps the form while the bootstrap has not answered yet', () => {
    renderAt('/login', stubAuth({ status: 'loading' }))

    expect(screen.getByLabelText('Email')).toBeInTheDocument()
  })
})
