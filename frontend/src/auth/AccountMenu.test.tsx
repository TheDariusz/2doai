import type { ReactNode } from 'react'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AccountMenu, LogoutButton } from './AccountMenu'
import { ApiError } from '../api/client'
import { LOGGED_IN as loggedIn, renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from './auth-context'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

function renderControl(control: ReactNode, auth: Auth) {
  renderWithAuth(
    <Routes>
      <Route path="/" element={control} />
      <Route path="/login" element={<p>login screen</p>} />
    </Routes>,
    { auth },
  )
}

/**
 * Everything the menu owns sits behind the account chip, which is named by the email it shows —
 * nothing below is reachable until it is opened, which is the point of the first test here.
 */
async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: loggedIn.user!.email }))
}

describe('AccountMenu', () => {
  it('keeps the account actions behind the chip until it is opened', async () => {
    renderControl(<AccountMenu />, stubAuth(loggedIn))
    const user = userEvent.setup()

    expect(screen.queryByRole('button', { name: 'Delete account' })).not.toBeInTheDocument()

    await openMenu(user)

    expect(screen.getByRole('button', { name: 'Delete account' })).toBeInTheDocument()
  })

  it('double-gates deletion: a confirm step plus password re-entry', async () => {
    const auth = stubAuth(loggedIn)
    renderControl(<AccountMenu />, auth)
    const user = userEvent.setup()
    await openMenu(user)

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
    renderControl(<AccountMenu />, stubAuth({ ...loggedIn, deleteAccount }))
    const user = userEvent.setup()
    await openMenu(user)

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    await user.type(screen.getByLabelText('Confirm with your password'), 'tajnehaslo')
    const submit = screen.getByRole('button', { name: 'Delete my account for good' })
    await user.click(submit)
    await user.click(submit)

    expect(deleteAccount).toHaveBeenCalledTimes(1)
  })

  async function submitDeletion(failure: ApiError) {
    renderControl(<AccountMenu />, stubAuth({ ...loggedIn, deleteAccount: async () => { throw failure } }))
    const user = userEvent.setup()
    await openMenu(user)

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

/** Ending the session is its own header control, so it is reached without opening the menu. */
describe('LogoutButton', () => {
  it('logs out and returns to the login screen', async () => {
    const auth = stubAuth(loggedIn)
    renderControl(<LogoutButton />, auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Log out' }))

    expect(auth.logout).toHaveBeenCalled()
    expect(await screen.findByText('login screen')).toBeInTheDocument()
  })

  it('reports a failed logout instead of pretending the session ended', async () => {
    const auth = stubAuth({
      ...loggedIn,
      logout: async () => { throw new ApiError(503, 'Service unavailable') },
    })
    renderControl(<LogoutButton />, auth)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Log out' }))

    // The server may still hold the session, so the user must not be told they are out.
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not log you out/i)
    expect(screen.queryByText('login screen')).not.toBeInTheDocument()
  })
})
