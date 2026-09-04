import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AccountMenu } from './AccountMenu'
import { ApiError } from '../api/client'
import i18n from '../i18n'
import { LOGGED_IN as loggedIn, renderWithAuth, response, stubAuth } from '../test/auth'
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

/** The signed-in user the language tests start from — the account is stored as English. */
const ENGLISH_ACCOUNT: Partial<Auth> = {
  ...loggedIn,
  user: { id: 'u1', email: 'ala@example.pl', language: 'EN' },
}

describe('AccountMenu — the account language (FR-002)', () => {
  it('stores the language the user picks and follows the answer the server gives', async () => {
    fetchMock.mockResolvedValue(response(200, { id: 'u1', email: 'ala@example.pl', language: 'PL' }))
    renderMenu(stubAuth(ENGLISH_ACCOUNT))

    await userEvent.setup().selectOptions(screen.getByLabelText('Language'), 'pl')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/users/me')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ language: 'PL' })
    // The response is what the screen follows, not the value that was asked for.
    expect(await screen.findByRole('button', { name: 'Wyloguj' })).toBeInTheDocument()
  })

  /**
   * The server writes unconditionally — it has to, because an update guarded on "only if it
   * differs" returns zero rows for a no-op, which the handler can only read as "no such account"
   * and answer 401. So the guard lives here, and it is not cosmetic: a query is what wakes the
   * metered database. This is also what any boot-time reconcile has to honour — it may move
   * i18next, never the column.
   */
  it('writes nothing when the language picked is the one the account already has', async () => {
    await i18n.changeLanguage('pl')
    renderMenu(stubAuth(ENGLISH_ACCOUNT))

    await userEvent.setup().selectOptions(screen.getByLabelText('Język'), 'en')

    expect(fetchMock).not.toHaveBeenCalled()
    // The pick is still honoured on screen — it was the app that disagreed with the account.
    expect(await screen.findByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('says so when the language cannot be stored, and stays in the language it was in', async () => {
    fetchMock.mockResolvedValue(response(500, { detail: 'boom' }))
    renderMenu(stubAuth(ENGLISH_ACCOUNT))

    await userEvent.setup().selectOptions(screen.getByLabelText('Language'), 'pl')

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not change the language. Try again.')
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
