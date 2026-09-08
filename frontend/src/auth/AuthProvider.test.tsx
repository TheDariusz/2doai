import { useState } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './auth-context'
import i18n from '../i18n'
import { response } from '../test/auth'

const fetchMock = vi.fn()

/** Surfaces the provider's state, and swallows the rejection so a failed logout is assertable. */
function Probe() {
  const { status, user, logout, changeLanguage, verify, resendCode } = useAuth()
  const [failed, setFailed] = useState(false)

  return (
    <>
      <p>status: {status}</p>
      <p>account: {user?.language}</p>
      {failed && <p>logout odrzucony</p>}
      <button onClick={() => logout().catch(() => setFailed(true))}>Wyloguj</button>
      <button onClick={() => void changeLanguage('pl')}>na polski</button>
      <button onClick={() => void changeLanguage('en')}>to English</button>
      <button onClick={() => void verify('ala@example.pl', '042317')}>spend the code</button>
      <button onClick={() => void resendCode('ala@example.pl')}>ask for another</button>
    </>
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

describe('AuthProvider', () => {
  it('reads a 401 bootstrap as anonymous, not as a failure', async () => {
    fetchMock.mockResolvedValue(response(401, { detail: 'Authentication is required' }))

    render(<AuthProvider><Probe /></AuthProvider>)

    expect(await screen.findByText('status: anonymous')).toBeInTheDocument()
  })

  it('restores the session from GET /users/me', async () => {
    fetchMock.mockResolvedValue(response(200, { id: 'u1', email: 'ala@example.pl' }))

    render(<AuthProvider><Probe /></AuthProvider>)

    expect(await screen.findByText('status: authenticated')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/users/me', expect.anything())
  })

  /**
   * After login the account decides what the app reads in; before it, the browser did. The
   * reconcile is deliberately one-way: an app that answered a disagreement with a `PATCH` would
   * write once per session forever, and a write is what wakes the metered database.
   */
  it('adopts the account language from /users/me without ever writing it back', async () => {
    fetchMock.mockResolvedValue(response(200, { id: 'u1', email: 'ala@example.pl', language: 'PL' }))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: authenticated')

    await waitFor(() => expect(i18n.resolvedLanguage).toBe('pl'))
    expect(fetchMock.mock.calls.every(([, init]) => (init?.method ?? 'GET') === 'GET')).toBe(true)
  })

  /**
   * FR-002 lives here rather than in the menu that offers it: `user.language` is the account's
   * language, so the write that moves it has to move the context's copy in the same step — the
   * screen then follows what the server *stored*, not what was asked for.
   */
  it('stores the language picked and follows the answer the server gives', async () => {
    fetchMock
      .mockResolvedValueOnce(response(200, { id: 'u1', email: 'ala@example.pl', language: 'EN' }))
      .mockResolvedValueOnce(response(200, { id: 'u1', email: 'ala@example.pl', language: 'PL' }))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: authenticated')
    await userEvent.setup().click(screen.getByRole('button', { name: 'na polski' }))

    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/users/me')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ language: 'PL' })
    expect(await screen.findByText('account: PL')).toBeInTheDocument()
    await waitFor(() => expect(i18n.resolvedLanguage).toBe('pl'))
  })

  /**
   * The server writes unconditionally — it has to, because an update guarded on "only if it
   * differs" returns zero rows for a no-op, which the handler can only read as "no such account"
   * and answer 401. So the guard lives here, and it is not cosmetic: a query is what wakes the
   * metered database.
   */
  it('writes nothing when the language picked is the one the account already has', async () => {
    fetchMock.mockResolvedValue(response(200, { id: 'u1', email: 'ala@example.pl', language: 'EN' }))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: authenticated')
    // Driven out of step *after* the bootstrap, because the bootstrap is what puts them in step:
    // `adopt` has already reconciled the app to the account by the time anything can be clicked.
    // Nothing is supposed to produce this state — which is precisely the state the branch is for,
    // and the only one in which it does anything at all.
    await act(() => i18n.changeLanguage('pl'))

    await userEvent.setup().click(screen.getByRole('button', { name: 'to English' }))

    expect(fetchMock.mock.calls.every(([, init]) => (init?.method ?? 'GET') === 'GET')).toBe(true)
    // The pick is still honoured — it was the app that disagreed with the account, not the user.
    await waitFor(() => expect(i18n.resolvedLanguage).toBe('en'))
  })

  /**
   * Both address-proof calls are public and answer with no body — the account they move cannot log
   * in yet, so there is nothing for the provider to adopt from either one.
   */
  it('spends a code through POST /verifications and leaves the session alone', async () => {
    fetchMock
      .mockResolvedValueOnce(response(401, { detail: 'Authentication is required' }))
      .mockResolvedValueOnce(response(204))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: anonymous')
    await userEvent.setup().click(screen.getByRole('button', { name: 'spend the code' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/verifications')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ email: 'ala@example.pl', code: '042317' })
    expect(screen.getByText('status: anonymous')).toBeInTheDocument()
  })

  it('asks for another code through POST /verification-codes', async () => {
    fetchMock
      .mockResolvedValueOnce(response(401, { detail: 'Authentication is required' }))
      .mockResolvedValueOnce(response(202))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: anonymous')
    await userEvent.setup().click(screen.getByRole('button', { name: 'ask for another' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/verification-codes')
    expect(init.method).toBe('POST')
    // The address alone — which account it belongs to is what the 202 declines to disclose.
    expect(JSON.parse(init.body)).toEqual({ email: 'ala@example.pl' })
  })

  it('drops to anonymous when a later call reports the session expired', async () => {
    fetchMock.mockResolvedValue(response(200, { id: 'u1', email: 'ala@example.pl' }))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: authenticated')

    act(() => window.dispatchEvent(new Event('session-expired')))

    expect(await screen.findByText('status: anonymous')).toBeInTheDocument()
  })

  it('treats a 401 on logout as a session that is already gone', async () => {
    fetchMock
      .mockResolvedValueOnce(response(200, { id: 'u1', email: 'ala@example.pl' }))
      .mockResolvedValueOnce(response(401, { detail: 'Authentication is required' }))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: authenticated')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Wyloguj' }))

    expect(await screen.findByText('status: anonymous')).toBeInTheDocument()
    expect(screen.queryByText('logout odrzucony')).not.toBeInTheDocument()
  })

  it('keeps the user signed in when logout fails for any other reason', async () => {
    fetchMock
      .mockResolvedValueOnce(response(200, { id: 'u1', email: 'ala@example.pl' }))
      .mockResolvedValueOnce(response(503, { detail: 'Service unavailable' }))

    render(<AuthProvider><Probe /></AuthProvider>)
    await screen.findByText('status: authenticated')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Wyloguj' }))

    // The DELETE never landed, so the server may still hold the session — do not clear it locally.
    expect(await screen.findByText('logout odrzucony')).toBeInTheDocument()
    expect(screen.getByText('status: authenticated')).toBeInTheDocument()
  })
})
