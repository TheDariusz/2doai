import { useState, type ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { AuthContext, type Auth } from './auth/auth-context'
import { response, stubAuth } from './test/auth'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(
    response(200, { items: [{ code: 'LEISURE', name_pl: 'Czas wolny i hobby' }] }),
  )
  vi.stubGlobal('fetch', fetchMock)
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

function LocationProbe() {
  const { pathname, search } = useLocation()
  return <p data-testid="location">{pathname + search}</p>
}

function renderApp(path: string, initial: Auth['status'] = 'anonymous') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Session initial={initial}>
        <AppRoutes />
        <LocationProbe />
      </Session>
    </MemoryRouter>,
  )
}

describe('AppRoutes', () => {
  it('returns a bounced visitor to the deep link they asked for, query string and all', async () => {
    renderApp('/domena/LEISURE?widok=tydzien')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'ala@example.pl')
    await user.type(screen.getByLabelText('Hasło'), 'tajnehaslo')
    await user.click(screen.getByRole('button', { name: 'Zaloguj się' }))

    // Not '/' — the whole location, query included, survives the round trip through /login.
    expect(await screen.findByRole('heading', { name: 'Czas wolny i hobby' })).toBeInTheDocument()
    expect(await screen.findByTestId('location')).toHaveTextContent('/domena/LEISURE?widok=tydzien')
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
