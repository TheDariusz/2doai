import { act, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import i18n from '../i18n'
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

    // Waited on by name: the static entries link is in the DOM before the fetch lands,
    // so `findAllByRole('link')` alone would resolve on a nav that has no domains in it yet.
    await screen.findByRole('link', { name: 'Health' })
    const links = screen
      .getAllByRole('link')
      .filter((link) => link.getAttribute('href')?.startsWith('/domain/'))

    expect(links).toHaveLength(11)
    expect(links.map((link) => link.textContent)).toEqual(DOMAINS.map((d) => d.name))
    expect(links[0]).toHaveAttribute('href', '/domain/health')
    expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.anything())
  })

  it('offers the three-layer screen alongside the domains', async () => {
    renderShell('/')

    expect(await screen.findByRole('link', { name: 'Tasks, goals and dreams' })).toHaveAttribute(
      'href',
      '/goals',
    )
  })

  it('says so when the domains cannot be loaded, rather than showing an empty nav', async () => {
    fetchMock.mockResolvedValue(response(500, { detail: 'boom' }))

    renderShell('/')

    expect(await screen.findByText(/could not load/i)).toBeInTheDocument()
  })

  /**
   * The notice was a one-shot state while the load ran once per mount. It re-runs per language now,
   * so it also has to be able to go back: a shell that recovered would otherwise keep telling the
   * user to refresh a page whose nav is sitting right underneath the message, fully loaded.
   *
   * The message is looked up through `t` rather than quoted, because the switch is what triggers the
   * reload — a hardcoded English string would go missing for the trivial reason that the page is now
   * in Polish, and the test would pass without the bug being fixed.
   */
  it('takes back the failure notice once a later load succeeds', async () => {
    fetchMock.mockResolvedValueOnce(response(500, { detail: 'boom' }))

    renderShell('/')
    await screen.findByText(i18n.t('layout.domainsFailed'))

    // The switch re-runs the effect; this second attempt gets the 200 from `beforeEach`.
    await act(() => i18n.changeLanguage('pl'))

    await screen.findByRole('link', { name: 'Health' })
    expect(screen.queryByText(i18n.t('layout.domainsFailed'))).not.toBeInTheDocument()
  })

  /**
   * Two loads in flight, answered out of order. Not exotic: the first request after a cold Fly
   * machine wakes can hang long enough for an impatient switch to overtake it, and the categories
   * endpoint answers the second one off a startup-built map. Whichever lands last would otherwise
   * win, leaving the nav in the language the user has already left.
   */
  it('ignores the answer to a load the language switch already replaced', async () => {
    let answerTheFirstLoad: (page: unknown) => void = () => {}
    fetchMock.mockImplementationOnce(
      () => new Promise((resolve) => (answerTheFirstLoad = resolve)),
    )
    fetchMock.mockResolvedValue(response(200, { items: [{ code: 'HEALTH', name: 'Zdrowie' }] }))

    renderShell('/')
    await act(() => i18n.changeLanguage('pl'))
    await screen.findByRole('link', { name: 'Zdrowie' })

    // The overtaken load finally answers — with the English labels it was sent to fetch.
    await act(async () => answerTheFirstLoad(response(200, { items: DOMAINS })))

    expect(screen.getByRole('link', { name: 'Zdrowie' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Health' })).not.toBeInTheDocument()
  })

  it('shows the account controls in the header', async () => {
    renderShell('/')

    expect(await screen.findByText('ala@example.pl')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('routes a domain to its placeholder, named from the shell data', async () => {
    renderShell('/domain/leisure')

    expect(await screen.findByRole('heading', { name: 'Leisure & hobbies' })).toBeInTheDocument()
    expect(screen.getByText(/later slice/i)).toBeInTheDocument()
  })
})
