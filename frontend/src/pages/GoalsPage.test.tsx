import { fireEvent, screen, within } from '@testing-library/react'
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
  due_date: null,
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
  due_date: null,
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

const ELECTRICITY = {
  id: 'g4',
  content: 'Zapłacić za prąd',
  layer: 'TASK',
  horizon: null,
  due_date: '2026-09-01',
  category_code: 'HOME',
  completed_at: null,
  created_at: '2026-08-17T12:00:00Z',
  updated_at: '2026-08-17T12:00:00Z',
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

function renderGoals(path = '/cele') {
  renderWithAuth(<AppRoutes />, { path, auth: stubAuth(LOGGED_IN) })
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
  return within(await screen.findByRole('form', { name: 'Nowy wpis' }))
}

/** The edit form, scoped the same way and for the same reason. */
function editForm() {
  return within(screen.getByRole('form', { name: 'Edytuj wpis' }))
}

/**
 * `<input type="date">` is set, not typed. `userEvent.type` enters one character at a time and
 * jsdom sanitizes every partial value ("2", "20", "202"…) back to the empty string, so the field
 * ends up blank — the assertion then fails for a reason that has nothing to do with the component.
 */
function setDate(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } })
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

  it('gives current tasks their own section, with the term on the entry', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals()

    const tasks = section('Zadania bieżące')
    expect(await tasks.findByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(tasks.getByText(/2026-09-01/)).toBeInTheDocument()
    expect(tasks.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()
    expect(section('Cele długoterminowe').queryByText('Zapłacić za prąd')).not.toBeInTheDocument()
  })

  it('says so when the list cannot be loaded, rather than rendering as if it were empty', async () => {
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
      due_date: null,
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

  /**
   * The mirror of the horizon test, and the reason both fields are conditional: the server refuses a
   * horizon and a term on the same entry with a 422, so a form that offered both would be a way to
   * build a request that cannot succeed.
   */
  it('asks for a term only when the entry is a task, and never beside a horizon', async () => {
    stubApi([])
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    expect(form.queryByLabelText('Termin')).not.toBeInTheDocument()

    await user.selectOptions(form.getByLabelText('Rodzaj'), 'TASK')
    expect(form.getByLabelText('Termin')).toBeInTheDocument()
    expect(form.queryByLabelText('Horyzont')).not.toBeInTheDocument()

    await user.selectOptions(form.getByLabelText('Rodzaj'), 'DREAM')
    expect(form.queryByLabelText('Termin')).not.toBeInTheDocument()
  })

  it('posts a task with its term', async () => {
    stubApi([])
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Treść'), 'Zapłacić za prąd')
    await user.selectOptions(form.getByLabelText('Rodzaj'), 'TASK')
    setDate(form.getByLabelText('Termin'), '2026-09-01')
    await user.selectOptions(form.getByLabelText('Kategoria'), 'HOME')
    await user.click(form.getByRole('button', { name: 'Dodaj' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Zapłacić za prąd',
      layer: 'TASK',
      horizon: null,
      due_date: '2026-09-01',
      category_code: 'HOME',
    })
  })

  /**
   * A term is optional — most tasks are "next", not "by Friday". An untouched date input reads as
   * the empty string, which the server rejects as a malformed date, so it has to leave as a null.
   */
  it('posts a task with no term as an explicit null, not an empty string', async () => {
    stubApi([])
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Treść'), 'Kupić chleb')
    await user.selectOptions(form.getByLabelText('Rodzaj'), 'TASK')
    await user.click(form.getByRole('button', { name: 'Dodaj' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Kupić chleb',
      layer: 'TASK',
      horizon: null,
      due_date: null,
      category_code: null,
    })
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
      due_date: null,
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

  /**
   * The delete is permanent server-side, so the confirmation is part of the behaviour, not chrome:
   * the cancelled half asserts that nothing was sent, which is the only way a broken guard shows up
   * — a `confirm` that is never consulted still passes every assertion about the confirmed path.
   */
  it('deletes an entry only once the user confirms, and the row goes with the refetch', async () => {
    const stored = [RUN]
    stubApi(stored)
    const user = userEvent.setup()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Usuń' }))

    expect(confirm).toHaveBeenCalled()
    expect(mutations()).toHaveLength(0)
    expect(screen.getByText('Przebiec półmaraton')).toBeInTheDocument()

    confirm.mockReturnValue(true)
    // What the refetch that follows the DELETE will find — the list, not local state, is what
    // removes the row.
    stored.length = 0
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Usuń' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals/g1')
    expect(init.method).toBe('DELETE')
    expect(screen.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()

  })

  it('converts a dream into a goal through the inline edit form', async () => {
    stubApi([JAPAN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Pojechać do Japonii')).getByRole('button', { name: 'Edytuj' }))

    const form = editForm()
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
      due_date: null,
      category_code: null,
      completed: false,
    })
    expect(screen.queryByRole('form', { name: 'Edytuj wpis' })).not.toBeInTheDocument()
  })

  /**
   * The conversions S-07 adds, in both directions — and the only place the two time fields can
   * collide. PUT is a full replace, so a layer switch that resent the field the old layer owned
   * would build a payload the server answers with 422: a task carrying the goal's horizon, or a
   * goal carrying the task's term. Both directions are asserted because they fail independently.
   */
  it('converts a goal into a task, dropping the horizon and picking up a term', async () => {
    stubApi([RUN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Edytuj' }))

    const form = editForm()
    await user.selectOptions(form.getByLabelText('Rodzaj'), 'TASK')
    setDate(form.getByLabelText('Termin'), '2026-09-01')
    await user.click(form.getByRole('button', { name: 'Zapisz' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Przebiec półmaraton',
      layer: 'TASK',
      horizon: null,
      due_date: '2026-09-01',
      category_code: 'HEALTH',
      completed: false,
    })
  })

  it("drops a task's term when it becomes a long-term goal", async () => {
    stubApi([ELECTRICITY])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Zapłacić za prąd')).getByRole('button', { name: 'Edytuj' }))

    const form = editForm()
    expect(form.getByLabelText('Termin')).toHaveValue('2026-09-01')

    await user.selectOptions(form.getByLabelText('Rodzaj'), 'GOAL')
    await user.selectOptions(form.getByLabelText('Horyzont'), 'FEW_MONTHS')
    await user.click(form.getByRole('button', { name: 'Zapisz' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Zapłacić za prąd',
      layer: 'GOAL',
      horizon: 'FEW_MONTHS',
      due_date: null,
      category_code: 'HOME',
      completed: false,
    })
  })
})

/**
 * PUT is a full replace, so every edit resends the whole entry. These pin the fields the form has
 * to carry along untouched — each one is invisible in the UI and silently destroyed if the payload
 * drops it, which no amount of clicking through the happy path would reveal.
 */
describe('GoalsPage — edycja zachowuje resztę wpisu', () => {
  it('keeps a completed entry completed when only its text is edited', async () => {
    stubApi([COOKING])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Nauczyć się gotować')).getByRole('button', { name: 'Edytuj' }))

    const form = editForm()
    await user.clear(form.getByLabelText('Treść'))
    await user.type(form.getByLabelText('Treść'), 'Nauczyć się gotować (poprawka)')
    await user.click(form.getByRole('button', { name: 'Zapisz' }))

    expect(JSON.parse(mutations()[0][1].body)).toMatchObject({
      content: 'Nauczyć się gotować (poprawka)',
      completed: true,
    })
  })

  it("carries a goal's horizon and category through a text-only edit", async () => {
    stubApi([RUN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Edytuj' }))

    const form = editForm()
    expect(form.getByLabelText('Horyzont')).toHaveValue('THIS_YEAR')
    expect(form.getByLabelText('Kategoria')).toHaveValue('HEALTH')

    await user.clear(form.getByLabelText('Treść'))
    await user.type(form.getByLabelText('Treść'), 'Przebiec maraton')
    await user.click(form.getByRole('button', { name: 'Zapisz' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Przebiec maraton',
      layer: 'GOAL',
      horizon: 'THIS_YEAR',
      due_date: null,
      category_code: 'HEALTH',
      completed: false,
    })
  })
})

/** Answers the goals GET normally and fails every mutation with the given status. */
function stubFailingMutations(goals: Goal[], status: number, detail = 'boom') {
  fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
    if (url === '/api/categories') return Promise.resolve(response(200, { items: DOMAINS }))
    if ((init.method ?? 'GET') === 'GET') return Promise.resolve(response(200, { items: [...goals] }))
    return Promise.resolve(response(status, { detail }))
  })
}

function goalFetches() {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/goals')
}

describe('GoalsPage — nieudany zapis', () => {
  it('says what is wrong when the server rejects the entry, not just "try again"', async () => {
    stubFailingMutations([RUN], 422)
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Ukończ' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/odrzuc/i)
  })

  it('refetches when the entry is already gone, so the stale row disappears', async () => {
    stubFailingMutations([RUN], 404)
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Przebiec półmaraton')
    const before = goalFetches().length

    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Ukończ' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/już nie istnieje/i)
    expect(goalFetches().length).toBeGreaterThan(before)
  })

  /**
   * The refetch that answers a 404 can fail too. When it does, the row is still on screen, so
   * "lista została odświeżona" is a claim the code cannot back up — and it used to overwrite the
   * banner that told the truth. Armed on the DELETE because that is the irreversible one: the user
   * needs to know whether what they see is current.
   */
  it('does not claim the list was refreshed when the refetch failed too', async () => {
    let refetch = false
    fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
      if (url === '/api/categories') return Promise.resolve(response(200, { items: DOMAINS }))
      if ((init.method ?? 'GET') === 'GET') {
        return Promise.resolve(refetch ? response(500, { detail: 'boom' }) : response(200, { items: [RUN] }))
      }
      refetch = true
      return Promise.resolve(response(404, { detail: 'No such goal' }))
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Usuń' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/nie udało się wczytać/i)
    expect(alert).not.toHaveTextContent(/odświeżona/i)
    expect(screen.getByText('Przebiec półmaraton')).toBeInTheDocument()
  })

  it('keeps what the user typed when the save fails', async () => {
    stubFailingMutations([], 500)
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Treść'), 'Przebiec półmaraton')
    await user.selectOptions(form.getByLabelText('Rodzaj'), 'DREAM')
    await user.click(form.getByRole('button', { name: 'Dodaj' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(form.getByLabelText('Treść')).toHaveValue('Przebiec półmaraton')
  })

  it('records the failure, so a save that breaks in production is not invisible', async () => {
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {})
    stubFailingMutations([RUN], 500)
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Ukończ' }))

    await screen.findByRole('alert')
    expect(logged).toHaveBeenCalled()
  })
})

/**
 * The filter controls, scoped by the region's accessible name — "Kategoria" is also the create
 * form's own field label, so an unscoped `getByLabelText` is ambiguous the moment the page renders.
 */
function filters() {
  return within(screen.getByRole('region', { name: 'Filtry' }))
}

describe('GoalsPage — filtry', () => {
  it('shows one layer at a time, heading and all, when narrowed by rodzaj', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Przebiec półmaraton')
    await user.selectOptions(filters().getByLabelText('Rodzaj'), 'TASK')

    expect(screen.getByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(screen.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()
    // The heading goes with its entries: an empty "Cele długoterminowe" would read as "no goals".
    expect(screen.queryByRole('heading', { name: 'Cele długoterminowe' })).not.toBeInTheDocument()
  })

  it('narrows by category across all three layers at once', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Przebiec półmaraton')
    await user.selectOptions(filters().getByLabelText('Kategoria'), 'HEALTH')

    expect(screen.getByText('Przebiec półmaraton')).toBeInTheDocument()
    expect(screen.queryByText('Zapłacić za prąd')).not.toBeInTheDocument()
    // Category cuts across the layers rather than replacing them — all three stay on screen.
    expect(screen.getByRole('heading', { name: 'Marzenia' })).toBeInTheDocument()
  })

  /**
   * `category_code` is nullable and the proposal engine treats null as one shared bucket, not as
   * eleven absences. Uncategorised entries therefore get a choice of their own: without it they are
   * reachable only by clearing the filter, which is indistinguishable from "there are none".
   */
  it('keeps uncategorised entries reachable, under an explicit choice of their own', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Przebiec półmaraton')
    await user.selectOptions(filters().getByLabelText('Kategoria'), 'NONE')

    expect(screen.getByText('Pojechać do Japonii')).toBeInTheDocument()
    expect(screen.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()
    expect(screen.queryByText('Zapłacić za prąd')).not.toBeInTheDocument()
  })

  /**
   * The filters live in the query string, not in React state: a reload, a back button and a link
   * pasted to yourself all land on the same view for no code of ours.
   */
  it('reads both filters off the URL, so a reload or a shared link lands on the same view', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/cele?layer=TASK&category=HOME')

    expect(await screen.findByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Marzenia' })).not.toBeInTheDocument()
    expect(filters().getByLabelText('Rodzaj')).toHaveValue('TASK')
    expect(filters().getByLabelText('Kategoria')).toHaveValue('HOME')
  })
})
