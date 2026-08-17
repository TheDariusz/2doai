import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Goal } from './GoalsPage'
import { AppRoutes } from '../App'
import { LOGGED_IN, renderWithAuth, response, stubAuth } from '../test/auth'
import { DOMAINS } from '../test/domains'

const RUN = {
  id: 'g1',
  content: 'Przebiec półmaraton',
  layer: 'GOAL',
  horizon: 'THIS_YEAR',
  category_code: 'HEALTH',
  completed_at: null,
  created_at: '2026-08-17T10:00:00Z',
  updated_at: '2026-08-17T10:00:00Z',
} satisfies Goal

const JAPAN = {
  id: 'g2',
  content: 'Pojechać do Japonii',
  layer: 'DREAM',
  horizon: null,
  category_code: null,
  completed_at: null,
  created_at: '2026-08-17T11:00:00Z',
  updated_at: '2026-08-17T11:00:00Z',
} satisfies Goal

const COOKING = {
  ...RUN,
  id: 'g3',
  content: 'Nauczyć się gotować',
  category_code: null,
  completed_at: '2026-08-16T09:00:00Z',
} satisfies Goal

const fetchMock = vi.fn()

/**
 * Answers the two GETs the screen makes — the shell's categories and the page's goals — and lets
 * every mutation succeed, so a test only has to say what the list contains. `goals` is read on each
 * call rather than captured, so pushing to it mid-test is what a refetch sees — copied per call,
 * because a real server sends a fresh list and React skips the re-render on an identical array.
 */
function stubApi(goals: Goal[]) {
  fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
    if (url === '/api/categories') return Promise.resolve(response(200, { items: DOMAINS }))
    if (url === '/api/goals' && (init.method ?? 'GET') === 'GET') {
      return Promise.resolve(response(200, { items: [...goals] }))
    }
    return Promise.resolve(response(200, {}))
  })
}

/** The mutation calls only — the two GETs are setup noise in every mutation assertion. */
function mutations() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

function renderGoals() {
  renderWithAuth(<AppRoutes />, { path: '/cele', auth: stubAuth(LOGGED_IN) })
}

/** The `<section>` a heading belongs to, so item assertions cannot match the other layer's list. */
function section(name: string) {
  return within(screen.getByRole('heading', { name }).closest('section') as HTMLElement)
}

/**
 * The create form, scoped by its accessible name — the edit form carries the same field labels, so
 * an unscoped `getByLabelText` becomes ambiguous the moment one is open.
 */
async function createForm() {
  return within(await screen.findByRole('form', { name: 'Nowy cel lub marzenie' }))
}

/** One entry's row, so "Ukończ" resolves to that entry's button and not the other three. */
async function item(content: string) {
  return within((await screen.findByText(content)).closest('li') as HTMLElement)
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

describe('GoalsPage', () => {
  it('groups active entries by layer and tucks completed ones into a collapsed section', async () => {
    stubApi([RUN, JAPAN, COOKING])

    renderGoals()

    const goals = section('Cele długoterminowe')
    expect(await goals.findByText('Przebiec półmaraton')).toBeInTheDocument()
    // The category shows its Polish label, not the wire code the server sends.
    expect(goals.getByText(/Zdrowie/)).toBeInTheDocument()
    expect(goals.queryByText('Pojechać do Japonii')).not.toBeInTheDocument()

    expect(section('Marzenia').getByText('Pojechać do Japonii')).toBeInTheDocument()

    const completed = screen.getByText('Nauczyć się gotować').closest('details')
    expect(completed).toBeInTheDocument()
    expect(completed).not.toHaveAttribute('open')
  })

  it('says so when the list cannot be loaded, rather than rendering as if it were empty', async () => {
    stubApi([])
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/categories'
          ? response(200, { items: DOMAINS })
          : response(500, { detail: 'boom' }),
      ),
    )

    renderGoals()

    expect(await screen.findByRole('alert')).toHaveTextContent(/nie udało się/i)
  })
})

describe('GoalsPage — dodawanie', () => {
  it('posts what the form was filled with and shows the entry after the refetch', async () => {
    const stored: Goal[] = []
    stubApi(stored)
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Treść'), 'Przebiec półmaraton')
    await user.selectOptions(form.getByLabelText('Rodzaj'), 'GOAL')
    await user.selectOptions(form.getByLabelText('Horyzont'), 'THIS_YEAR')
    await user.selectOptions(form.getByLabelText('Kategoria'), 'HEALTH')
    // What the refetch that follows the POST will find.
    stored.push(RUN)
    await user.click(form.getByRole('button', { name: 'Dodaj' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({
      content: 'Przebiec półmaraton',
      layer: 'GOAL',
      horizon: 'THIS_YEAR',
      category_code: 'HEALTH',
    })
    expect(await screen.findByText('Przebiec półmaraton')).toBeInTheDocument()
  })

  it('asks for a horizon only when the entry is a goal', async () => {
    stubApi([])
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    expect(form.getByLabelText('Horyzont')).toBeInTheDocument()

    await user.selectOptions(form.getByLabelText('Rodzaj'), 'DREAM')
    // A dream with a horizon is a 422 from the server — do not offer the field at all.
    expect(form.queryByLabelText('Horyzont')).not.toBeInTheDocument()

    await user.selectOptions(form.getByLabelText('Rodzaj'), 'GOAL')
    expect(form.getByLabelText('Horyzont')).toBeInTheDocument()
  })

  it('offers the 11 domains plus an explicit "no category" choice', async () => {
    stubApi([])

    renderGoals()
    const form = await createForm()
    const options = within(form.getByLabelText('Kategoria')).getAllByRole('option')

    expect(options.map((option) => option.textContent)).toEqual([
      'Bez kategorii',
      ...DOMAINS.map((domain) => domain.name_pl),
    ])
  })
})

describe('GoalsPage — zmiany na wpisie', () => {
  it('completes an entry without dropping the rest of the full-replace payload', async () => {
    stubApi([RUN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Ukończ' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals/g1')
    expect(init.method).toBe('PUT')
    // PUT is a full replace: sending only `completed` would blank the content and the category.
    expect(JSON.parse(init.body)).toEqual({
      content: 'Przebiec półmaraton',
      layer: 'GOAL',
      horizon: 'THIS_YEAR',
      category_code: 'HEALTH',
      completed: true,
    })
  })

  it('reopens a completed entry from the collapsed section', async () => {
    stubApi([COOKING])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Nauczyć się gotować')).getByRole('button', { name: 'Przywróć' }))

    expect(JSON.parse(mutations()[0][1].body)).toMatchObject({ completed: false })
  })

  it('converts a dream into a goal through the inline edit form', async () => {
    stubApi([JAPAN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Pojechać do Japonii')).getByRole('button', { name: 'Edytuj' }))

    const form = within(screen.getByRole('form', { name: 'Edytuj wpis' }))
    expect(form.getByLabelText('Treść')).toHaveValue('Pojechać do Japonii')
    // A dream has no horizon to prefill — the field only appears once it becomes a goal.
    expect(form.queryByLabelText('Horyzont')).not.toBeInTheDocument()

    await user.selectOptions(form.getByLabelText('Rodzaj'), 'GOAL')
    await user.selectOptions(form.getByLabelText('Horyzont'), 'FEW_MONTHS')
    await user.click(form.getByRole('button', { name: 'Zapisz' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals/g2')
    expect(JSON.parse(init.body)).toEqual({
      content: 'Pojechać do Japonii',
      layer: 'GOAL',
      horizon: 'FEW_MONTHS',
      category_code: null,
      completed: false,
    })
    expect(screen.queryByRole('form', { name: 'Edytuj wpis' })).not.toBeInTheDocument()
  })
})
