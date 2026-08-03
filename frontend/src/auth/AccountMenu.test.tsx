import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AccountMenu } from './AccountMenu'
import { ApiError } from '../api/client'
import { LOGGED_IN as loggedIn, renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from './auth-context'

function renderMenu(auth: Auth) {
  renderWithAuth(
    <Routes>
      <Route path="/" element={<AccountMenu />} />
      <Route path="/login" element={<p>ekran logowania</p>} />
    </Routes>,
    { auth },
  )
}

describe('AccountMenu', () => {
  it('logs out and returns to the login screen', async () => {
    const auth = stubAuth(loggedIn)
    renderMenu(auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Wyloguj' }))

    expect(auth.logout).toHaveBeenCalled()
    expect(await screen.findByText('ekran logowania')).toBeInTheDocument()
  })

  it('reports a failed logout instead of pretending the session ended', async () => {
    const auth = stubAuth({
      ...loggedIn,
      logout: async () => { throw new ApiError(503, 'Service unavailable') },
    })
    renderMenu(auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Wyloguj' }))

    // The server may still hold the session, so the user must not be told they are out.
    expect(await screen.findByRole('alert')).toHaveTextContent(/wylogować/i)
    expect(screen.queryByText('ekran logowania')).not.toBeInTheDocument()
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

  it('does not fire a second deletion while the first is in flight', async () => {
    const deleteAccount = vi.fn().mockReturnValue(new Promise<void>(() => {}))
    renderMenu(stubAuth({ ...loggedIn, deleteAccount }))
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Usuń konto' }))
    await user.type(screen.getByLabelText('Potwierdź hasłem'), 'tajnehaslo')
    const submit = screen.getByRole('button', { name: 'Usuń konto na zawsze' })
    await user.click(submit)
    await user.click(submit)

    expect(deleteAccount).toHaveBeenCalledTimes(1)
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
