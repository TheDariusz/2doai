import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes, type InitialEntry } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { VerifyPage } from './VerifyPage'
import type { VerifyHandover } from './verifyHandover'
import { ApiError } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import { LOGGED_IN, renderWithAuth, response, stubAuth } from '../test/auth'
import { LocationProbe } from '../test/LocationProbe'
import { type Auth } from '../auth/auth-context'

/** The screen, plus the places a session sends the user on to. */
function renderVerify(auth: Auth, entry: InitialEntry = '/verify') {
  renderWithAuth(
    <>
      <Routes>
        <Route path="/verify" element={<VerifyPage />} />
        <Route path="/goals" element={<p>the app</p>} />
        <Route path="/domain/leisure" element={<p>the domain</p>} />
      </Routes>
      <LocationProbe />
      {/* The mail link followed in a tab that already shows the screen: same path, a new fragment. */}
      <Link to="/verify#email=ala%40example.pl&code=55551234">the mail link</Link>
    </>,
    { path: entry, auth },
  )
}

function handedOver(state: VerifyHandover): InitialEntry {
  return { pathname: '/verify', state }
}

async function signIn(code: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Code from the email'), code)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

afterEach(() => {
  vi.useRealTimers()
})

describe('VerifyPage — signing in', () => {
  it('opens a session with the code and lands in the app', async () => {
    const auth = stubAuth()
    renderVerify(auth, handedOver({ email: 'ala@example.pl' }))

    expect(screen.getByText(/eight-digit code to it/)).toHaveTextContent('ala@example.pl')
    await signIn('04231789')

    expect(auth.signInWithCode).toHaveBeenCalledWith('ala@example.pl', '04231789')
    expect(await screen.findByText('the app')).toBeInTheDocument()
  })

  it('returns the user to the route they were bounced from', async () => {
    const auth = stubAuth()
    renderVerify(auth, handedOver({ email: 'ala@example.pl', from: { pathname: '/domain/leisure' } }))

    await signIn('04231789')

    expect(await screen.findByText('the domain')).toBeInTheDocument()
  })

  /**
   * Wrong, expired, spent and unknown are one 401 by design — so the copy has to be the one thing
   * the user can do about any of them, which is ask for another code.
   */
  it('reports a refused code as a refused code, whatever the reason was', async () => {
    const auth = stubAuth({
      signInWithCode: async () => {
        throw new ApiError(401, 'Authentication is required or credentials are invalid')
      },
    })
    renderVerify(auth, handedOver({ email: 'ala@example.pl' }))

    await signIn('00000000')

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer valid/i)
    expect(screen.getByRole('heading', { name: 'Enter the code' })).toBeInTheDocument()
  })

  it('says signing in is down, not that a code could not be sent, when the sign-in itself is a 503', async () => {
    const auth = stubAuth({ signInWithCode: async () => { throw new ApiError(503, 'Service unavailable') } })
    renderVerify(auth, handedOver({ email: 'ala@example.pl' }))

    await signIn('04231789')

    expect(await screen.findByRole('alert')).toHaveTextContent(/signing in is unavailable/i)
  })

  it('sends a visitor who already has a session on to the app', async () => {
    renderVerify(stubAuth(LOGGED_IN), handedOver({ email: 'ala@example.pl' }))

    expect(await screen.findByText('the app')).toBeInTheDocument()
  })
})

describe('VerifyPage — the mail link', () => {
  it('prefills address and code from the fragment, then clears it from the address bar', async () => {
    renderVerify(stubAuth(), '/verify#email=ala%2Btodo%40example.pl&code=04231789')

    expect(screen.getByLabelText('Email')).toHaveValue('ala+todo@example.pl')
    expect(screen.getByLabelText('Code from the email')).toHaveValue('04231789')
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/verify$/)
  })

  /**
   * The link only prefills: nothing is spent until the button is pressed, so a mail scanner that
   * follows it signs nobody in and burns no guess.
   */
  it('does not sign in on its own — only the button does', async () => {
    const auth = stubAuth()
    renderVerify(auth, '/verify#email=ala%40example.pl&code=04231789')

    await screen.findByText(/^\/verify$/)
    expect(auth.signInWithCode).not.toHaveBeenCalled()

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))
    expect(auth.signInWithCode).toHaveBeenCalledWith('ala@example.pl', '04231789')
  })

  /**
   * A phone that reuses its open tab, or the installed app, delivers the link as a hash change on a
   * screen that is already mounted — no remount, so whatever was read on arrival is all it knows.
   * Found in a local walk: the fragment was stripped and the code it carried went with it.
   */
  it('takes a link followed while the screen is already open, not just one it was opened with', async () => {
    const auth = stubAuth()
    renderVerify(auth, handedOver({ email: 'ala@example.pl', resendAfter: 60 }))
    const user = userEvent.setup()

    await user.click(screen.getByRole('link', { name: 'the mail link' }))

    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/verify$/)
    expect(screen.getByLabelText('Code from the email')).toHaveValue('55551234')
    expect(auth.signInWithCode).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(auth.signInWithCode).toHaveBeenCalledWith('ala@example.pl', '55551234')
  })

  it('leaves the code empty when the fragment carries a mangled one', () => {
    renderVerify(stubAuth(), '/verify#email=ala%40example.pl&code=0423')

    expect(screen.getByLabelText('Email')).toHaveValue('ala@example.pl')
    expect(screen.getByLabelText('Code from the email')).toHaveValue('')
  })
})

describe('VerifyPage — asking for another code', () => {
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

  /**
   * The server refuses a second code inside a minute anyway; locking the button for that minute
   * turns a refusal the user has to read into a wait they can see.
   */
  it('locks the resend for a minute after a send, counting down, then unlocks it', async () => {
    // The clock moves only when the test moves it: a clock that also followed real time made every
    // assertion here a race against how loaded the machine running the suite was.
    vi.useFakeTimers()
    const auth = stubAuth()
    renderVerify(auth, handedOver({ email: 'ala@example.pl', resendAfter: 60 }))
    const resend = screen.getByRole('button', { name: 'Send the code again' })

    expect(resend).toBeDisabled()
    expect(screen.getByText(/another code in 1:00/)).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(18_000))
    expect(screen.getByText(/another code in 0:42/)).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(42_000))
    expect(resend).toBeEnabled()
    expect(screen.queryByText(/another code in/)).not.toBeInTheDocument()

    // `fireEvent`, not user-event: the latter paces itself on timers this test has frozen. The click
    // returns once the event is dispatched, not once the resend it started has settled — so flush
    // that promise chain, without moving the clock, before reading what it rendered.
    fireEvent.click(resend)
    await act(() => vi.advanceTimersByTimeAsync(0))
    expect(auth.resendCode).toHaveBeenCalledWith('ala@example.pl')
    expect(screen.getByRole('status')).toHaveTextContent(/on its way/i)
    expect(resend).toBeDisabled()
    expect(screen.getByText(/another code in 1:00/)).toBeInTheDocument()
  })

  it('honours the wait a 429 names in Retry-After', async () => {
    const auth = stubAuth({
      resendCode: async () => {
        throw new ApiError(429, 'Too many verification codes requested', undefined, 1800)
      },
    })
    renderVerify(auth, handedOver({ email: 'ala@example.pl' }))

    await userEvent.setup().click(screen.getByRole('button', { name: 'Send the code again' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many code requests/i)
    expect(screen.getByRole('button', { name: 'Send the code again' })).toBeDisabled()
    expect(screen.getByText(/another code in 30:00/)).toBeInTheDocument()
  })

  /**
   * The same click through the real client, because a stubbed `resendCode` cannot see what broke
   * this button in production: the server answers 202 with no body, and reading a body that is not
   * there throws. Resend is the only recovery path on this screen, so it is worth the provider.
   */
  it('confirms a resend the server accepted with 202 and no body', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(response(401, { detail: 'Authentication is required' }))
        .mockResolvedValueOnce(response(202)),
    )
    render(
      <MemoryRouter initialEntries={['/verify']}>
        <AuthProvider>
          <VerifyPage />
        </AuthProvider>
      </MemoryRouter>,
    )

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email'), 'ala@example.pl')
    await user.click(screen.getByRole('button', { name: 'Send the code again' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/on its way/i)
  })
})
