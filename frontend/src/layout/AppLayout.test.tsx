import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLayout } from './AppLayout'
import { ApiError } from '../api/client'
import type { Auth } from '../auth/auth-context'
import i18n from '../i18n'
import { DomainPlaceholder } from '../pages/DomainPlaceholder'
import { LOGGED_IN, renderWithAuth, response, stubAuth } from '../test/auth'
import { DOMAINS } from '../test/domains'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockResolvedValue(response(200, { items: DOMAINS }))
  vi.stubGlobal('fetch', fetchMock)
})

/**
 * Stands in for the screen the rail filters. What a tag or the withdrawn switch *writes* is the
 * whole of its behaviour, so the URL it lands on is all this shell test needs — `GoalsPage` has its
 * own suite, and mounting it here would add its reads to every assertion in this file.
 */
function LocationProbe() {
  const { pathname, search } = useLocation()
  return <p data-testid="location">{pathname + search}</p>
}

function renderShell(path = '/goals', auth: Auth = stubAuth(LOGGED_IN)) {
  renderWithAuth(
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/goals" element={<LocationProbe />} />
        <Route path="/domain/:code" element={<DomainPlaceholder />} />
      </Route>
    </Routes>,
    { path, auth },
  )
}

/** A rail tag, waited on by name — the tags land only once the categories request answers. */
function tag(name: string) {
  return screen.findByRole('link', { name })
}

/** The URL the probe is reading, whole: a filter that dropped an axis still matches a substring. */
function landedOn() {
  return screen.getByTestId('location').textContent
}

describe('AppLayout', () => {
  it('renders one tag per domain, in the order the server sends, each filtering the entries', async () => {
    renderShell()

    await tag('Health')
    // The unfiltered tag links to the bare screen, so every other link is a domain.
    const tags = screen.getAllByRole('link').filter((link) => link.getAttribute('href') !== '/goals')

    expect(tags).toHaveLength(11)
    expect(tags.map((link) => link.textContent)).toEqual(DOMAINS.map((domain) => domain.name))
    expect(tags[0]).toHaveAttribute('href', '/goals?category=health')
    expect(fetchMock).toHaveBeenCalledWith('/api/categories', expect.anything())
  })

  /**
   * The query string is a link a user reads, edits and pastes, so the casing they land on is not
   * necessarily the lowercase the app writes — the tag has to recognise its own code either way.
   */
  it('marks the tag whose code the URL carries, whatever case it is written in', async () => {
    renderShell('/goals?category=HEALTH')

    expect(await tag('Health')).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'All' })).not.toHaveAttribute('aria-current')
  })

  it('marks the unfiltered tag when the URL names no category', async () => {
    renderShell()

    expect(await tag('All')).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Health' })).not.toHaveAttribute('aria-current')
  })

  /**
   * The rail is one axis of three. Switching domain must not silently widen the other two — the
   * user would be looking at a list they never asked for, with no control on screen saying so.
   */
  it('keeps the other filters the screen carries when the category changes', async () => {
    renderShell('/goals?layer=task&withdrawn=1')

    expect(await tag('Health')).toHaveAttribute(
      'href',
      '/goals?layer=task&withdrawn=1&category=health',
    )
    expect(screen.getByRole('link', { name: 'All' })).toHaveAttribute(
      'href',
      '/goals?layer=task&withdrawn=1',
    )
  })

  it('switches the withdrawn axis on and off without dropping the category being looked at', async () => {
    renderShell('/goals?category=health')
    const user = userEvent.setup()

    await user.click(await screen.findByLabelText('Show withdrawn'))
    expect(landedOn()).toBe('/goals?category=health&withdrawn=1')

    await user.click(screen.getByLabelText('Show withdrawn'))
    expect(landedOn()).toBe('/goals?category=health')
  })

  it('says so when the domains cannot be loaded, rather than showing an empty rail', async () => {
    fetchMock.mockResolvedValue(response(500, { detail: 'boom' }))

    renderShell()

    expect(await screen.findByText(/could not load/i)).toBeInTheDocument()
  })

  /**
   * The notice was a one-shot state while the load ran once per mount. It re-runs per language now,
   * so it also has to be able to go back: a shell that recovered would otherwise keep telling the
   * user to refresh a page whose rail is sitting right underneath the message, fully loaded.
   *
   * The message is looked up through `t` rather than quoted, because the switch is what triggers the
   * reload — a hardcoded English string would go missing for the trivial reason that the page is now
   * in Polish, and the test would pass without the bug being fixed.
   */
  it('takes back the failure notice once a later load succeeds', async () => {
    fetchMock.mockResolvedValueOnce(response(500, { detail: 'boom' }))

    renderShell()
    await screen.findByText(i18n.t('layout.domainsFailed'))

    // The switch re-runs the effect; this second attempt gets the 200 from `beforeEach`.
    await act(() => i18n.changeLanguage('pl'))

    await tag('Health')
    expect(screen.queryByText(i18n.t('layout.domainsFailed'))).not.toBeInTheDocument()
  })

  /**
   * Two loads in flight, answered out of order. Not exotic: the first request after a cold Fly
   * machine wakes can hang long enough for an impatient switch to overtake it, and the categories
   * endpoint answers the second one off a startup-built map. Whichever lands last would otherwise
   * win, leaving the rail in the language the user has already left.
   */
  it('ignores the answer to a load the language switch already replaced', async () => {
    let answerTheFirstLoad: (page: unknown) => void = () => {}
    fetchMock.mockImplementationOnce(
      () => new Promise((resolve) => (answerTheFirstLoad = resolve)),
    )
    fetchMock.mockResolvedValue(response(200, { items: [{ code: 'HEALTH', name: 'Zdrowie' }] }))

    renderShell()
    await act(() => i18n.changeLanguage('pl'))
    await tag('Zdrowie')

    // The overtaken load finally answers — with the English labels it was sent to fetch.
    await act(async () => answerTheFirstLoad(response(200, { items: DOMAINS })))

    expect(screen.getByRole('link', { name: 'Zdrowie' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Health' })).not.toBeInTheDocument()
  })

  it('shows the language, the account and the way out in the header', async () => {
    renderShell()

    expect(await screen.findByRole('group', { name: 'Language' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ala@example.pl' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it('routes a domain to its placeholder, named from the shell data', async () => {
    renderShell('/domain/leisure')

    expect(await screen.findByRole('heading', { name: 'Leisure & hobbies' })).toBeInTheDocument()
    expect(screen.getByText(/later slice/i)).toBeInTheDocument()
    // Off the entries screen nothing is filtered, so no tag claims to be the view you are on.
    expect(screen.getByRole('link', { name: 'All' })).not.toHaveAttribute('aria-current')
  })
})

/**
 * The header offers the pick and hands it to the context; what a pick *costs* — the PATCH, the
 * no-op guard, the account the answer moves — belongs to whatever owns `user.language`, and is
 * pinned in `AuthProvider.test.tsx` against real fetches.
 */
describe('AppLayout — the account language (FR-002)', () => {
  it('hands the pick to the context that owns the account language', async () => {
    const auth = stubAuth(LOGGED_IN)
    renderShell('/goals', auth)
    await tag('Health')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Polski' }))

    expect(auth.changeLanguage).toHaveBeenCalledWith('pl')
  })

  it('says so when the language cannot be stored, and stays in the language it was in', async () => {
    const changeLanguage = vi.fn().mockRejectedValue(new ApiError(500, 'boom'))
    renderShell('/goals', stubAuth({ ...LOGGED_IN, changeLanguage }))
    await tag('Health')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Polski' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not change the language. Try again.',
    )
    // The switch reads the live i18next language, so a failed write leaves the screen where it was.
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })
})
