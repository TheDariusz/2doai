import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './AppLayout'
import { DomainPlaceholder } from '../pages/DomainPlaceholder'
import { stubAuth } from '../test/auth'
import { AuthContext } from '../auth/auth-context'

/**
 * The 11 seeded categories, verbatim from `V2__seed_categories.sql` (codes are the English
 * `LifeDomain` constants; `name_pl` is the label). Deliberately shuffled — the shell must impose
 * display_order.
 */
const DOMAINS = [
  { code: 'FINANCE', name_pl: 'Finanse', display_order: 2 },
  { code: 'RELATIONSHIPS', name_pl: 'Relacje', display_order: 5 },
  { code: 'HEALTH', name_pl: 'Zdrowie', display_order: 1 },
  { code: 'CAREER', name_pl: 'Kariera i rozwój zawodowy', display_order: 3 },
  { code: 'EDUCATION', name_pl: 'Edukacja i rozwój osobisty', display_order: 4 },
  { code: 'HOME', name_pl: 'Dom i otoczenie', display_order: 6 },
  { code: 'LEISURE', name_pl: 'Czas wolny i hobby', display_order: 7 },
  { code: 'ADMIN', name_pl: 'Sprawy formalne i administracyjne', display_order: 8 },
  { code: 'SAFETY', name_pl: 'Bezpieczeństwo i przygotowanie na sytuacje awaryjne', display_order: 9 },
  { code: 'TRANSPORT', name_pl: 'Transport i mobilność', display_order: 10 },
  { code: 'INNER_GROWTH', name_pl: 'Rozwój wewnętrzny / wartości', display_order: 11 },
]

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue({
    ok: true,
    status: 200,
    statusText: '',
    json: async () => ({ items: DOMAINS }),
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function renderShell(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext value={stubAuth({ status: 'authenticated', user: { id: 'u1', email: 'ala@example.pl' } })}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<p>Wybierz domenę z nawigacji.</p>} />
            <Route path="domena/:code" element={<DomainPlaceholder />} />
          </Route>
        </Routes>
      </AuthContext>
    </MemoryRouter>,
  )
}

describe('AppLayout', () => {
  it('renders the 11 domains in display_order', async () => {
    renderShell('/')

    const links = await screen.findAllByRole('link')

    expect(links).toHaveLength(11)
    expect(links.map((link) => link.textContent)).toEqual([
      'Zdrowie', 'Finanse', 'Kariera i rozwój zawodowy', 'Edukacja i rozwój osobisty',
      'Relacje', 'Dom i otoczenie', 'Czas wolny i hobby', 'Sprawy formalne i administracyjne',
      'Bezpieczeństwo i przygotowanie na sytuacje awaryjne', 'Transport i mobilność',
      'Rozwój wewnętrzny / wartości',
    ])
    expect(links[0]).toHaveAttribute('href', '/domena/HEALTH')
    expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.anything())
  })

  it('says so when the domains cannot be loaded, rather than showing an empty nav', async () => {
    fetchMock.mockResolvedValue({
      ok: false, status: 500, statusText: '', json: async () => ({ detail: 'boom' }),
    })

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
