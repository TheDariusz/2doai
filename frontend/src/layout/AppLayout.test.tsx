import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { LOGGED_IN, renderWithAuth, response, stubAuth } from '../test/auth'
import { DOMAINS } from '../test/domains'

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

    // Waited on by name: the static "Cele i marzenia" link is in the DOM before the fetch lands,
    // so `findAllByRole('link')` alone would resolve on a nav that has no domains in it yet.
    await screen.findByRole('link', { name: 'Zdrowie' })
    const links = screen
      .getAllByRole('link')
      .filter((link) => link.getAttribute('href')?.startsWith('/domena/'))

    expect(links).toHaveLength(11)
    expect(links.map((link) => link.textContent)).toEqual(DOMAINS.map((d) => d.name_pl))
    expect(links[0]).toHaveAttribute('href', '/domena/HEALTH')
    expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.anything())
  })

  it('offers the goals screen alongside the domains', async () => {
    renderShell('/')

    expect(await screen.findByRole('link', { name: 'Cele i marzenia' })).toHaveAttribute(
      'href',
      '/cele',
    )
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
