import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthPage } from './AuthPage'
import { ApiError } from '../api/client'
import { stubAuth } from '../test/auth'
import { AuthContext, type Auth } from '../auth/auth-context'

function renderAt(path: '/login' | '/register', auth: Auth) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext value={auth}>
        <Routes>
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route path="/" element={<p>aplikacja</p>} />
        </Routes>
      </AuthContext>
    </MemoryRouter>,
  )
}

async function fillIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Hasło'), password)
  await user.click(screen.getByRole('button', { name: /zaloguj się|załóż konto/i }))
}

describe('AuthPage — logowanie', () => {
  it('submits the credentials and lands in the app', async () => {
    const auth = stubAuth()
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'tajnehaslo')

    expect(auth.login).toHaveBeenCalledWith('ala@example.pl', 'tajnehaslo')
    expect(await screen.findByText('aplikacja')).toBeInTheDocument()
  })

  it('shows a generic message on 401 — never which half was wrong', async () => {
    const auth = stubAuth({ login: async () => { throw new ApiError(401, 'Authentication is required or credentials are invalid') } })
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'zlehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent('Nieprawidłowy email lub hasło.')
  })

  it('does not quote the registration password rule when login is rejected (422)', async () => {
    const auth = stubAuth({ login: async () => { throw new ApiError(422, 'Validation failed') } })
    renderAt('/login', auth)

    await fillIn('ala@example.pl', 'x')

    // LoginRequest has no minimum — advice the user cannot act on is worse than none.
    expect(await screen.findByRole('alert')).not.toHaveTextContent(/8 znaków/)
  })
})

describe('AuthPage — rejestracja', () => {
  it('registers and sends the user to the login screen', async () => {
    const auth = stubAuth()
    renderAt('/register', auth)

    await fillIn('nowa@example.pl', 'tajnehaslo')

    expect(auth.register).toHaveBeenCalledWith('nowa@example.pl', 'tajnehaslo')
    expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeInTheDocument()
  })

  it('maps 409 to "email already in use, log in instead"', async () => {
    const auth = stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } })
    renderAt('/register', auth)

    await fillIn('zajety@example.pl', 'tajnehaslo')

    expect(await screen.findByRole('alert')).toHaveTextContent(/zajęty/i)
  })

  it('mirrors the server contract client-side: email format and an 8-character minimum', () => {
    renderAt('/register', stubAuth())

    expect(screen.getByLabelText('Email')).toHaveAttribute('type', 'email')
    expect(screen.getByLabelText('Hasło')).toHaveAttribute('minlength', '8')
  })
})
