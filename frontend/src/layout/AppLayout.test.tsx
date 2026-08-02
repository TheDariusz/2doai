import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { LOGGED_IN, renderWithAuth, response, stubAuth } from '../test/auth'

/**
 * The 11 seeded categories, verbatim from `V2__seed_categories.sql` (codes are the English
 * `LifeDomain` constants; `name_pl` is the label), in the `display_order` the server sorts by.
 */
const DOMAINS = [
  { code: 'HEALTH', name_pl: 'Zdrowie' },
  { code: 'FINANCE', name_pl: 'Finanse' },
  { code: 'CAREER', name_pl: 'Kariera i rozwój zawodowy' },
  { code: 'EDUCATION', name_pl: 'Edukacja i rozwój osobisty' },
  { code: 'RELATIONSHIPS', name_pl: 'Relacje' },
  { code: 'HOME', name_pl: 'Dom i otoczenie' },
  { code: 'LEISURE', name_pl: 'Czas wolny i hobby' },
  { code: 'ADMIN', name_pl: 'Sprawy formalne i administracyjne' },
  { code: 'SAFETY', name_pl: 'Bezpieczeństwo i przygotowanie na sytuacje awaryjne' },
  { code: 'TRANSPORT', name_pl: 'Transport i mobilność' },
  { code: 'INNER_GROWTH', name_pl: 'Rozwój wewnętrzny / wartości' },
]

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(response(200, { items: DOMAINS }))
  vi.stubGlobal('fetch', fetchMock)
})

function renderShell(path: string) {
  renderWithAuth(<AppRoutes />, { path, auth: stubAuth(LOGGED_IN) })
}

describe('AppLayout', () => {
  it('renders the 11 domains in the order the server sends', async () => {
    renderShell('/')

    const links = await screen.findAllByRole('link')

    expect(links).toHaveLength(11)
    expect(links.map((link) => link.textContent)).toEqual(DOMAINS.map((d) => d.name_pl))
    expect(links[0]).toHaveAttribute('href', '/domena/HEALTH')
    expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.anything())
  })

  it('says so when the domains cannot be loaded, rather than showing an empty nav', async () => {
    fetchMock.mockResolvedValue(response(500, { detail: 'boom' }))

    renderShell('/')

    expect(await screen.findByText(/nie udało się wczytać/i)).toBeInTheDocument()
  })

  it('shows the account controls in the header', async () => {
    renderShell('/')

    expect(await screen.findByText('ala@example.pl')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Wyloguj' })).toBeInTheDocument()
  })

  it('routes a domain to its placeholder, named from the shell data', async () => {
    renderShell('/domena/LEISURE')

    expect(await screen.findByRole('heading', { name: 'Czas wolny i hobby' })).toBeInTheDocument()
    expect(screen.getByText(/kolejnym wycinku/i)).toBeInTheDocument()
  })
})
