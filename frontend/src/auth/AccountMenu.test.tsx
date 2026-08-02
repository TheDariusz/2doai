import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AccountMenu } from './AccountMenu'
import { ApiError } from '../api/client'
import { stubAuth } from '../test/auth'
import { AuthContext, type Auth } from './auth-context'

function renderMenu(auth: Auth) {
  render(
    <MemoryRouter initialEntries={['/']}>
      <AuthContext value={auth}>
        <Routes>
          <Route path="/" element={<AccountMenu />} />
          <Route path="/login" element={<p>ekran logowania</p>} />
        </Routes>
      </AuthContext>
    </MemoryRouter>,
  )
}

const loggedIn = { status: 'authenticated' as const, user: { id: 'u1', email: 'ala@example.pl' } }

describe('AccountMenu', () => {
  it('logs out and returns to the login screen', async () => {
    const auth = stubAuth(loggedIn)
    renderMenu(auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Wyloguj' }))

    expect(auth.logout).toHaveBeenCalled()
    expect(await screen.findByText('ekran logowania')).toBeInTheDocument()
  })

  it('double-gates deletion: a confirm step plus password re-entry', async () => {
    const auth = stubAuth(loggedIn)
    renderMenu(auth)
    const user = userEvent.setup()

    // Nothing is deletable before the confirmation is opened.
    expect(screen.queryByLabelText('Potwierdź hasłem')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Usuń konto' }))
    await user.type(screen.getByLabelText('Potwierdź hasłem'), 'tajnehaslo')
    await user.click(screen.getByRole('button', { name: 'Usuń konto na zawsze' }))

    expect(auth.deleteAccount).toHaveBeenCalledWith('tajnehaslo')
    expect(await screen.findByText('ekran logowania')).toBeInTheDocument()
  })

  it('reports a mistyped password (403) without ending the session', async () => {
    const auth = stubAuth({
      ...loggedIn,
      deleteAccount: async () => { throw new ApiError(403, 'Re-authentication failed') },
    })
    renderMenu(auth)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Usuń konto' }))
    await user.type(screen.getByLabelText('Potwierdź hasłem'), 'zlehaslo')
    await user.click(screen.getByRole('button', { name: 'Usuń konto na zawsze' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/hasło/i)
    expect(screen.queryByText('ekran logowania')).not.toBeInTheDocument()
  })
})
