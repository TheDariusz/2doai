import { useState, type ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { AuthContext, type Auth } from './auth/auth-context'
import { stubAuth } from './test/auth'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    statusText: '',
    json: async () => ({ items: [{ code: 'LEISURE', name_pl: 'Czas wolny i hobby', display_order: 7 }] }),
  })
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=token-123'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; max-age=0'
})

/** Auth that actually flips on login, so the post-login redirect can be walked end to end. */
function Session({ children, initial }: { children: ReactNode; initial: Auth['status'] }) {
  const [status, setStatus] = useState(initial)

  return (
    <AuthContext
      value={stubAuth({
        status,
        user: status === 'authenticated' ? { id: 'u1', email: 'ala@example.pl' } : null,
        login: async () => setStatus('authenticated'),
      })}
    >
      {children}
    </AuthContext>
  )
}

function renderApp(path: string, initial: Auth['status'] = 'anonymous') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Session initial={initial}>
        <AppRoutes />
      </Session>
    </MemoryRouter>,
  )
}

describe('AppRoutes', () => {
  it('returns a bounced visitor to the page they originally asked for', async () => {
    renderApp('/domena/LEISURE')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'ala@example.pl')
    await user.type(screen.getByLabelText('Hasło'), 'tajnehaslo')
    await user.click(screen.getByRole('button', { name: 'Zaloguj się' }))

    // Not '/' — the deep link survives the round trip through /login.
    expect(await screen.findByRole('heading', { name: 'Czas wolny i hobby' })).toBeInTheDocument()
  })

  it('sends an unknown path home, and an anonymous visitor on to /login', async () => {
    renderApp('/nie-ma-takiej-sciezki')

    expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeInTheDocument()
  })

  it('points an authenticated user at the navigation from the index route', async () => {
    renderApp('/', 'authenticated')

    expect(await screen.findByText('Wybierz domenę z nawigacji.')).toBeInTheDocument()
  })
})
