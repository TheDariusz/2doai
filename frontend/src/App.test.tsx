import { useState, type ReactNode } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { ApiError } from './api/client'
import { AuthContext, type Auth } from './auth/auth-context'
import { LOGGED_IN, renderWithAuth, response, stubAuth } from './test/auth'
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

  /**
   * `/login` and `/register` are the same component in two modes at the same position in the route
   * tree, so React reconciles them as one instance unless the routes say otherwise — and a sign-up
   * that just failed leaves its message, and the address-confirmation link the 409 offers, sitting
   * on a sign-in screen the user has submitted nothing to.
   */
  it('does not carry a failed sign-up over to the sign-in screen', async () => {
    renderWithAuth(<AppRoutes />, {
      path: '/register',
      auth: stubAuth({ register: async () => { throw new ApiError(409, 'Email already registered') } }),
    })
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'zajety@example.pl')
    await user.type(screen.getByLabelText('Password'), 'tajnehaslo')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)

    await user.click(screen.getByRole('link', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /confirm your address/i })).not.toBeInTheDocument()
  })

  /** There is one screen to be on, so the index route is a redirect rather than a page of advice. */
  it('sends an authenticated user from the index route to the entries screen', async () => {
    // Nothing to show for any read the screen makes — this is about where it lands, not what is
    // on it. The proposal card's pending slot answers 204, the way an empty slot does.
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(url === '/api/proposals/pending' ? response(204) : response(200, { items: [] })),
    )

    renderApp('/', 'authenticated')

    expect(await screen.findByRole('heading', { name: 'Tasks, goals and dreams' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/goals')
  })
})
