import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './AppLayout'
import { DomainPlaceholder } from '../pages/DomainPlaceholder'
import { stubAuth } from '../test/auth'
import { AuthContext } from '../auth/auth-context'

/** The 11 frozen life domains, deliberately shuffled — the shell must impose display_order. */
const DOMAINS = [
  { code: 'ZDROWIE', name_pl: 'Zdrowie', display_order: 2 },
  { code: 'ROZWOJ', name_pl: 'Rozwój osobisty', display_order: 5 },
  { code: 'RODZINA', name_pl: 'Rodzina', display_order: 1 },
  { code: 'PRACA', name_pl: 'Praca', display_order: 3 },
  { code: 'FINANSE', name_pl: 'Finanse', display_order: 4 },
  { code: 'PRZYJACIELE', name_pl: 'Przyjaciele', display_order: 6 },
  { code: 'DOM', name_pl: 'Dom', display_order: 7 },
  { code: 'HOBBY', name_pl: 'Hobby', display_order: 8 },
  { code: 'DUCHOWOSC', name_pl: 'Duchowość', display_order: 9 },
  { code: 'PODROZE', name_pl: 'Podróże', display_order: 10 },
  { code: 'SPOLECZNOSC', name_pl: 'Społeczność', display_order: 11 },
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
      'Rodzina', 'Zdrowie', 'Praca', 'Finanse', 'Rozwój osobisty', 'Przyjaciele',
      'Dom', 'Hobby', 'Duchowość', 'Podróże', 'Społeczność',
    ])
    expect(links[0]).toHaveAttribute('href', '/domena/RODZINA')
    expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.anything())
  })

  it('shows the account controls in the header', async () => {
    renderShell('/')

    expect(await screen.findByText('ala@example.pl')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Wyloguj' })).toBeInTheDocument()
  })

  it('routes a domain to its placeholder, named from the shell data', async () => {
    renderShell('/domena/HOBBY')

    expect(await screen.findByRole('heading', { name: 'Hobby' })).toBeInTheDocument()
    expect(screen.getByText(/kolejnym wycinku/i)).toBeInTheDocument()
  })
})
