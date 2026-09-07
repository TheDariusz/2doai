import { useState, type ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { AuthContext, type Auth } from './auth/auth-context'
import { LOGGED_IN, response, stubAuth } from './test/auth'
import { DOMAINS } from './test/domains'

const fetchMock = vi.fn()

/** The one domain this suite routes to, from the shared fixture — a relabelled category is one edit. */
const LEISURE = DOMAINS.find((domain) => domain.code === 'LEISURE')!

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(response(200, { items: [LEISURE] }))
  vi.stubGlobal('fetch', fetchMock)
})

/** Auth that actually flips on login, so the post-login redirect can be walked end to end. */
function Session({ children, initial }: { children: ReactNode; initial: Auth['status'] }) {
  const [status, setStatus] = useState(initial)

  return (
    <AuthContext
      value={stubAuth({
        status,
        user: status === 'authenticated' ? LOGGED_IN.user : null,
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
    renderApp('/domain/leisure?view=week')
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Email'), 'ala@example.pl')
    await user.type(screen.getByLabelText('Password'), 'tajnehaslo')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    // Not '/' — the whole location, query included, survives the round trip through /login.
    expect(await screen.findByRole('heading', { name: LEISURE.name })).toBeInTheDocument()
    expect(await screen.findByTestId('location')).toHaveTextContent('/domain/leisure?view=week')
  })

  it('sends an unknown path home, and an anonymous visitor on to /login', async () => {
    renderApp('/no-such-path')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('points an authenticated user at the navigation from the index route', async () => {
    renderApp('/', 'authenticated')

    expect(await screen.findByText('Pick a life domain from the navigation.')).toBeInTheDocument()
  })
})
