import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AccountMenu } from './AccountMenu'
import { ApiError } from '../api/client'
import { LOGGED_IN as loggedIn, renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from './auth-context'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

function renderMenu(auth: Auth) {
  renderWithAuth(
    <Routes>
      <Route path="/" element={<AccountMenu />} />
      <Route path="/login" element={<p>login screen</p>} />
    </Routes>,
    { auth },
  )
}

describe('AccountMenu — the account language (FR-002)', () => {
  /**
   * The menu offers the pick and hands it to the context; what a pick *costs* — the PATCH, the
   * no-op guard, the account the answer moves — belongs to whatever owns `user.language`, and is
   * pinned in `AuthProvider.test.tsx` against real fetches.
   */
  it('hands the pick to the context that owns the account language', async () => {
    const auth = stubAuth(loggedIn)
    renderMenu(auth)

    await userEvent.setup().selectOptions(screen.getByLabelText('Language'), 'pl')

    expect(auth.changeLanguage).toHaveBeenCalledWith('pl')
  })

  it('says so when the language cannot be stored, and stays in the language it was in', async () => {
    const changeLanguage = vi.fn().mockRejectedValue(new ApiError(500, 'boom'))
    renderMenu(stubAuth({ ...loggedIn, changeLanguage }))

    await userEvent.setup().selectOptions(screen.getByLabelText('Language'), 'pl')

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not change the language. Try again.')
    // The switch reads the live i18next language, so a failed write leaves the screen where it was.
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })
})

describe('AccountMenu', () => {
  it('logs out and returns to the login screen', async () => {
    const auth = stubAuth(loggedIn)
    renderMenu(auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Log out' }))

    expect(auth.logout).toHaveBeenCalled()
    expect(await screen.findByText('login screen')).toBeInTheDocument()
  })

  it('reports a failed logout instead of pretending the session ended', async () => {
    const auth = stubAuth({
      ...loggedIn,
      logout: async () => { throw new ApiError(503, 'Service unavailable') },
    })
    renderMenu(auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Log out' }))

    // The server may still hold the session, so the user must not be told they are out.
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not log you out/i)
    expect(screen.queryByText('login screen')).not.toBeInTheDocument()
  })

  it('double-gates deletion: a confirm step plus password re-entry', async () => {
    const auth = stubAuth(loggedIn)
    renderMenu(auth)
    const user = userEvent.setup()

    // Nothing is deletable before the confirmation is opened.
    expect(screen.queryByLabelText('Confirm with your password')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    await user.type(screen.getByLabelText('Confirm with your password'), 'tajnehaslo')
    await user.click(screen.getByRole('button', { name: 'Delete my account for good' }))

    expect(auth.deleteAccount).toHaveBeenCalledWith('tajnehaslo')
    expect(await screen.findByText('login screen')).toBeInTheDocument()
  })

  it('does not fire a second deletion while the first is in flight', async () => {
    const deleteAccount = vi.fn().mockReturnValue(new Promise<void>(() => {}))
    renderMenu(stubAuth({ ...loggedIn, deleteAccount }))
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    await user.type(screen.getByLabelText('Confirm with your password'), 'tajnehaslo')
    const submit = screen.getByRole('button', { name: 'Delete my account for good' })
    await user.click(submit)
    await user.click(submit)

    expect(deleteAccount).toHaveBeenCalledTimes(1)
  })

  async function submitDeletion(failure: ApiError) {
    renderMenu(stubAuth({ ...loggedIn, deleteAccount: async () => { throw failure } }))
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    await user.type(screen.getByLabelText('Confirm with your password'), 'zlehaslo')
    await user.click(screen.getByRole('button', { name: 'Delete my account for good' }))
  }

  it('names the mistyped password (403 + the re-auth type) without ending the session', async () => {
    await submitDeletion(
      new ApiError(403, 'The password you entered is incorrect', 'urn:2doai:problem:re-auth-failed'),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect password.')
    expect(screen.queryByText('login screen')).not.toBeInTheDocument()
  })

  it('stays generic on a 403 that is not the re-auth type (a stale CSRF token)', async () => {
    // No `type` at all — Boot 4 omits the member for an untyped ProblemDetail rather than
    // serializing about:blank, as AuthApiTest.rejectsAnAuthenticatedMutationCarryingNoCsrfToken pins.
    await submitDeletion(new ApiError(403, 'The authenticated request is not allowed'))

    // Nothing here may blame the password — this 403 is the CSRF filter's, not a wrong password.
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not delete the account. Refresh the page and try again.',
    )
    expect(screen.queryByText('login screen')).not.toBeInTheDocument()
  })
})
