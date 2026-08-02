import { useState } from 'react'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './auth-context'

const fetchMock = vi.fn()

function response(status: number, body?: unknown) {
  return { ok: status < 400, status, statusText: '', json: async () => body }
}

/** Surfaces the provider's state, and swallows the rejection so a failed logout is assertable. */
function Probe() {
  const { status, logout } = useAuth()
  const [failed, setFailed] = useState(false)

  return (
    <>
      <p>status: {status}</p>
      {failed && <p>logout odrzucony</p>}
      <button onClick={() => logout().catch(() => setFailed(true))}>Wyloguj</button>
    </>
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=token-123'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; max-age=0'
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
