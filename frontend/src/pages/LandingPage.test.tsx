import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import i18n from '../i18n'
import { renderWithAuth, stubAuth } from '../test/auth'
import { CHROME } from '../theme'
import { LandingPage } from './LandingPage'

const fetchMock = vi.fn()
beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

it.each([
  ['en', 'Your everyday tasks. Dreams that come true.', 'Example with fictional entries', 'Run a marathon', 'Made by', 'Theme'],
  ['pl', 'Codzienne zadania. Marzenia, które się spełniają.', 'Przykład z fikcyjnymi wpisami', 'Przebiec maraton', 'Autor', 'Motyw'],
] as const)('renders the static content in %s without private requests or pretend controls', async (language, heading, caption, dream, credit, theme) => {
  await act(() => i18n.changeLanguage(language))
  renderWithAuth(<LandingPage />)

  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(heading)
  const preview = screen.getByRole('figure', { name: caption })
  expect(within(preview).getByText(dream)).toBeInTheDocument()
  expect(within(preview).getAllByRole('heading', { level: 3 })).toHaveLength(4)
  expect(within(preview).queryByRole('button')).not.toBeInTheDocument()
  expect(within(preview).queryByRole('link')).not.toBeInTheDocument()
  expect(within(screen.getByRole('banner')).getByRole('group', { name: theme })).toBeInTheDocument()
  expect(screen.getByRole('main')).toBeInTheDocument()
  expect(screen.getByRole('contentinfo')).toHaveTextContent(credit)
  expect(fetchMock).not.toHaveBeenCalled()
})

it('credits the developer in a footer whose links open their site and profiles in new tabs', () => {
  renderWithAuth(<LandingPage />)

  const footer = screen.getByRole('contentinfo')
  expect(screen.getByRole('main')).not.toContainElement(footer)
  expect(footer).toHaveTextContent('Dariusz Szczepański')
  expect(footer).toHaveTextContent('Software Engineer')
  // The profile links are icons, so each one's accessible name is the site it opens.
  const expected: Record<string, string> = {
    TheDariusz: 'https://thedariusz.dev',
    'thedariusz.dev': 'https://thedariusz.dev',
    GitHub: 'https://github.com/thedariusz',
    LinkedIn: 'https://www.linkedin.com/in/dariusz-szczepanski-dev',
  }
  const links = within(footer).getAllByRole('link')
  expect(links.map((link) => link.getAttribute('aria-label') ?? link.textContent)).toEqual(Object.keys(expected))
  for (const link of links) {
    expect(link).toHaveAttribute('href', expected[link.getAttribute('aria-label') ?? link.textContent ?? ''])
    expect(link).toHaveAttribute('target', '_blank')
    // Not `noreferrer`: the author's site should see where its visitors come from.
    expect(link).toHaveAttribute('rel', 'noopener')
  }
})

it('points account actions and the explanation anchor to their destinations', async () => {
  renderWithAuth(<LandingPage />)

  const signup = screen.getAllByRole('link', { name: 'Create account' })
  expect(signup).toHaveLength(2)
  signup.forEach((link) => expect(link).toHaveAttribute('href', '/register'))
  expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
  const explanation = screen.getByRole('link', { name: 'See how it works' })
  expect(explanation).toHaveAttribute('href', '#how-it-works')
  expect(document.getElementById('how-it-works')).toHaveTextContent('How it works')
})

it.each(['login', 'register'] as const)('keeps the local language when following the %s link without a settings write', async (mode) => {
  // The link and the screen it opens share one label, so the heading proves where the click landed.
  const label = i18n.t(`auth.${mode}.heading`, { lng: 'pl' })
  const auth = stubAuth()
  renderWithAuth(<AppRoutes />, { auth })
  const user = userEvent.setup()

  await user.click(screen.getByRole('button', { name: 'Polski' }))
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Codzienne zadania. Marzenia, które się spełniają.')
  expect(localStorage.getItem('2doai.language')).toBe('pl')
  await user.click(screen.getAllByRole('link', { name: label })[0])

  expect(screen.getByRole('heading', { level: 1, name: label })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Polski' })).toHaveAttribute('aria-pressed', 'true')
  expect(auth.changeLanguage).not.toHaveBeenCalled()
  expect(fetchMock).not.toHaveBeenCalled()
})

it('applies and keeps a theme picked in the header without a settings write', async () => {
  // Like the language, the choice is local: there is no account to store it on yet.
  const auth = stubAuth()
  renderWithAuth(<LandingPage />, { auth })
  const user = userEvent.setup()

  await user.click(within(screen.getByRole('banner')).getByRole('button', { name: 'Dark' }))

  expect(screen.getByRole('button', { name: 'Dark' })).toHaveAttribute('aria-pressed', 'true')
  expect(document.documentElement.dataset.theme).toBe('dark')
  expect(localStorage.getItem('2doai.theme')).toBe('dark')
  expect(auth.changeLanguage).not.toHaveBeenCalled()
  expect(fetchMock).not.toHaveBeenCalled()
})

it('leaves the status-bar colour to the theme in force rather than pinning it light', () => {
  // theme.ts keeps this meta in step with data-theme on every page; the landing used to override it.
  const meta = document.createElement('meta')
  meta.name = 'theme-color'
  meta.content = CHROME.dark
  document.head.appendChild(meta)

  const { unmount } = renderWithAuth(<LandingPage />)
  expect(meta.content).toBe(CHROME.dark)
  unmount()
  expect(meta.content).toBe(CHROME.dark)
  meta.remove()
})
