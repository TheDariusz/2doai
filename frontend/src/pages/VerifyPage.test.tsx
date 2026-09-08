import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthPage } from './AuthPage'
import { VerifyPage } from './VerifyPage'
import { ApiError } from '../api/client'
import { VERIFICATION_FAILED } from '../auth/problems'
import { renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from '../auth/auth-context'

/** The screen, plus the one it hands over to — the sign-in notice is half of what success means. */
function renderVerify(auth: Auth, state?: { email?: string; error?: string }) {
  renderWithAuth(
    <Routes>
      <Route path="/verify" element={<VerifyPage />} />
      <Route path="/login" element={<AuthPage mode="login" />} />
    </Routes>,
    { path: { pathname: '/verify', state }, auth },
  )
}

describe('VerifyPage', () => {
  it('spends the code and hands the user to sign-in, saying the address is confirmed', async () => {
    const auth = stubAuth()
    renderVerify(auth, { email: 'ala@example.pl' })

    expect(screen.getByText(/six-digit code to ala@example\.pl/)).toBeInTheDocument()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the email'), '042317')
    await user.click(screen.getByRole('button', { name: 'Confirm address' }))

    expect(auth.verify).toHaveBeenCalledWith('ala@example.pl', '042317')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/confirmed/i)
  })

  /**
   * Wrong, expired, spent and unknown are one answer by design — so the copy has to be the one
   * thing the user can do about any of them, which is ask for another code.
   */
  it('reports a refused code as a refused code, whatever the reason was', async () => {
    const auth = stubAuth({
      verify: async () => {
        throw new ApiError(403, 'Verification failed', VERIFICATION_FAILED)
      },
    })
    renderVerify(auth, { email: 'ala@example.pl' })

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Code from the email'), '000000')
    await user.click(screen.getByRole('button', { name: 'Confirm address' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer valid/i)
  })

  it('answers a throttled resend with the wait, not with a failure', async () => {
    const auth = stubAuth({
      resendCode: async () => {
        throw new ApiError(429, 'Too many verification codes requested')
      },
    })
    renderVerify(auth, { email: 'ala@example.pl' })

    await userEvent.setup().click(screen.getByRole('button', { name: 'Send the code again' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/wait a minute/i)
  })

  /** A reload loses the route state, and the screen is the only way to a new code — so it asks. */
  it('keeps the address editable, so a reload can still ask for another code', async () => {
    const auth = stubAuth()
    renderVerify(auth)

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email'), 'ala@example.pl')
    await user.click(screen.getByRole('button', { name: 'Send the code again' }))

    expect(auth.resendCode).toHaveBeenCalledWith('ala@example.pl')
    expect(await screen.findByRole('status')).toHaveTextContent(/on its way/i)
  })
})
