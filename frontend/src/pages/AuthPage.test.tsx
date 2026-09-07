import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthPage } from './AuthPage'
import { ApiError } from '../api/client'
import { renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from '../auth/auth-context'

function renderAt(path: '/login' | '/register', auth: Auth) {
  renderWithAuth(
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
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

  it('does not quote the registration password rule when login is rejected (422)', async () => {
    const auth = stubAuth({ login: async () => { throw new ApiError(422, 'Validation failed') } })
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'x')

    // LoginRequest has no minimum — advice the user cannot act on is worse than none.
    expect(await screen.findByRole('alert')).not.toHaveTextContent(/8 characters/)
  })
})

describe('AuthPage — registering', () => {
  it('registers and sends the user to the login screen', async () => {
    const auth = stubAuth()
    renderAt('/register', auth)

    await fillIn('nowa@example.pl', 'tajnehaslo')

    expect(auth.register).toHaveBeenCalledWith('nowa@example.pl', 'tajnehaslo')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('maps 409 to "email already in use, log in instead"', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } })
    renderAt('/register', auth)

    await fillIn('zajety@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)
  })

  it('mirrors the server contract client-side: email format and an 8-character minimum', () => {
    renderAt('/register', stubAuth())

    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText('Password')).toHaveAttribute('minlength', '8')
  })
})
