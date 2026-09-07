import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Goal } from './GoalsPage'
import type { Proposal } from './ProposalCard'
import { AppRoutes } from '../App'
import i18n from '../i18n'
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
  remind_after: null,
  withdrawn_at: null,
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
  remind_after: null,
  withdrawn_at: null,
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

/**
 * Withdrawn, not completed — FR-013's "never" is a third state, and the difference is the whole
 * point of the filter below: a completed entry is finished, a withdrawn one was never started and
 * the user said it never will be.
 */
const GUITAR = {
  ...JAPAN,
  id: 'g5',
  content: 'Nauczyć się grać na gitarze',
  withdrawn_at: '2026-08-20T08:00:00Z',
} satisfies Goal

const ELECTRICITY = {
  id: 'g4',
  content: 'Zapłacić za prąd',
  layer: 'TASK',
  horizon: null,
  due_date: '2026-09-01',
  category_code: 'HOME',
  completed_at: null,
  remind_after: null,
  withdrawn_at: null,
  created_at: '2026-08-17T12:00:00Z',
  updated_at: '2026-08-17T12:00:00Z',
} satisfies Goal

const fetchMock = vi.fn()

/**
 * The two reads that happen before any test here is about anything: the shell's categories and the
 * proposal card's pending slot. Neither is ever what a GoalsPage test asserts and both answer the
 * same in all of them, so every stub below hands them back first — and a card that got the goals
 * list by accident would crash rendering an entry that is not a proposal.
 */
function backdrop(url: string) {
  if (url === '/api/categories') return response(200, { items: DOMAINS })
  if (url === '/api/proposals/pending') return response(204)
  return null
}

/**
 * Answers the GETs the screen makes — the backdrop above, and the page's goals — and lets every
 * mutation succeed, so a test only has to say what the list contains. `goals` is read on each call
 * rather than captured, so pushing to it mid-test is what a refetch sees — copied per call, because
 * a real server sends a fresh list and React skips the re-render on an identical array.
 */
function stubApi(goals: Goal[]) {
  fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
    const fixed = backdrop(url)
    if (fixed) return Promise.resolve(fixed)
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

/** Renders the current URL, so the filters' *writes* can be asserted and not just their reads. */
function LocationProbe() {
  const { pathname, search } = useLocation()
  return <span data-testid="location">{pathname + search}</span>
}

function renderGoals(path = '/goals') {
  renderWithAuth(
    <>
      <AppRoutes />
      <LocationProbe />
    </>,
    { path, auth: stubAuth(LOGGED_IN) },
  )
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
  return within(await screen.findByRole('form', { name: 'New entry' }))
}

/** The edit form, scoped the same way and for the same reason. */
function editForm() {
  return within(screen.getByRole('form', { name: 'Edit entry' }))
}

/**
 * The layer a form is being filled for. The picker is a segmented control, so choosing one is a
 * press rather than a select — and it is scoped to its own group, because the title row's layer
 * tabs answer to the same three names.
 */
function kind(form: ReturnType<typeof within>, name: string) {
  return within(form.getByLabelText('Kind')).getByRole('button', { name })
}

/** The title row's layer tabs, scoped by the group's accessible name. */
function layerTabs() {
  return within(screen.getByRole('group', { name: 'Show kind' }))
}

/** The tab that is on — the segmented control's answer to a `<select>`'s value. */
function chosenLayer() {
  return layerTabs()
    .getAllByRole('button')
    .find((tab) => tab.getAttribute('aria-pressed') === 'true')
}

/**
 * `<input type="date">` is set, not typed. `userEvent.type` enters one character at a time and
 * jsdom sanitizes every partial value ("2", "20", "202"…) back to the empty string, so the field
 * ends up blank — the assertion then fails for a reason that has nothing to do with the component.
 */
function setDate(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } })
}

/** One entry's row, so the complete button resolves to that entry's and not the other three. */
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

    const goals = section('Long-term goals')
    expect(await goals.findByText('Przebiec półmaraton')).toBeInTheDocument()
    // The category shows its label, not the wire code the server sends.
    expect(goals.getByText(/Health/)).toBeInTheDocument()
    expect(goals.queryByText('Pojechać do Japonii')).not.toBeInTheDocument()

    expect(section('Dreams').getByText('Pojechać do Japonii')).toBeInTheDocument()

    const completed = screen.getByText('Nauczyć się gotować').closest('details')
    expect(completed).toBeInTheDocument()
    expect(completed).not.toHaveAttribute('open')
  })

  it('gives current tasks their own section, with the term on the entry', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals()

    const tasks = section('Current tasks')
    expect(await tasks.findByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(tasks.getByText(/2026-09-01/)).toBeInTheDocument()
    expect(tasks.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()
    expect(section('Long-term goals').queryByText('Zapłacić za prąd')).not.toBeInTheDocument()
  })

  it('says so when the list cannot be loaded, rather than rendering as if it were empty', async () => {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(backdrop(url) ?? response(500, { detail: 'boom' })),
    )

    renderGoals()

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not/i)
  })
})

describe('GoalsPage — adding an entry', () => {
  /**
   * The bar names all three layers, because it is the one control on the screen a user meets before
   * they know the app has layers at all. The field keeps its accessible name behind the placeholder
   * — a placeholder disappears the moment anything is typed, so it can never be the only label.
   */
  it('invites all three layers into one field, without dropping the field’s name', async () => {
    stubApi([])

    renderGoals()
    const form = await createForm()

    expect(form.getByPlaceholderText('Add a task, a goal or a dream…')).toBe(
      form.getByLabelText('Content'),
    )
  })

  it('posts what the form was filled with and shows the entry after the refetch', async () => {
    const stored: Goal[] = []
    stubApi(stored)
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Content'), 'Przebiec półmaraton')
    await user.click(kind(form, 'Goal'))
    await user.selectOptions(form.getByLabelText('Horizon'), 'THIS_YEAR')
    await user.selectOptions(form.getByLabelText('Category'), 'HEALTH')
    // What the refetch that follows the POST will find.
    stored.push(RUN)
    await user.click(form.getByRole('button', { name: 'Add' }))

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
    expect(form.getByLabelText('Horizon')).toBeInTheDocument()

    await user.click(kind(form, 'Dream'))
    // A dream with a horizon is a 422 from the server — do not offer the field at all.
    expect(form.queryByLabelText('Horizon')).not.toBeInTheDocument()

    await user.click(kind(form, 'Goal'))
    expect(form.getByLabelText('Horizon')).toBeInTheDocument()
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
    expect(form.queryByLabelText('Due date')).not.toBeInTheDocument()

    await user.click(kind(form, 'Task'))
    expect(form.getByLabelText('Due date')).toBeInTheDocument()
    expect(form.queryByLabelText('Horizon')).not.toBeInTheDocument()

    await user.click(kind(form, 'Dream'))
    expect(form.queryByLabelText('Due date')).not.toBeInTheDocument()
  })

  it('posts a task with its term', async () => {
    stubApi([])
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Content'), 'Zapłacić za prąd')
    await user.click(kind(form, 'Task'))
    setDate(form.getByLabelText('Due date'), '2026-09-01')
    await user.selectOptions(form.getByLabelText('Category'), 'HOME')
    await user.click(form.getByRole('button', { name: 'Add' }))

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
    await user.type(form.getByLabelText('Content'), 'Kupić chleb')
    await user.click(kind(form, 'Task'))
    await user.click(form.getByRole('button', { name: 'Add' }))

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
    const options = within(form.getByLabelText('Category')).getAllByRole('option')

    expect(options.map((option) => option.textContent)).toEqual([
      'No category',
      ...DOMAINS.map((domain) => domain.name),
    ])
  })
})

describe('GoalsPage — changing an entry', () => {
  it('completes an entry without dropping the rest of the full-replace payload', async () => {
    stubApi([RUN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Complete' }))

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
      withdrawn: false,
    })
  })

  it('reopens a completed entry from the collapsed section', async () => {
    stubApi([COOKING])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Nauczyć się gotować')).getByRole('button', { name: 'Restore' }))

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
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Delete' }))

    expect(confirm).toHaveBeenCalled()
    expect(mutations()).toHaveLength(0)
    expect(screen.getByText('Przebiec półmaraton')).toBeInTheDocument()

    confirm.mockReturnValue(true)
    // What the refetch that follows the DELETE will find — the list, not local state, is what
    // removes the row.
    stored.length = 0
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Delete' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals/g1')
    expect(init.method).toBe('DELETE')
    expect(screen.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()

  })

  it('converts a dream into a goal through the inline edit form', async () => {
    stubApi([JAPAN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Pojechać do Japonii')).getByRole('button', { name: 'Edit' }))

    const form = editForm()
    expect(form.getByLabelText('Content')).toHaveValue('Pojechać do Japonii')
    // A dream has no horizon to prefill — the field only appears once it becomes a goal.
    expect(form.queryByLabelText('Horizon')).not.toBeInTheDocument()

    await user.click(kind(form, 'Goal'))
    await user.selectOptions(form.getByLabelText('Horizon'), 'FEW_MONTHS')
    await user.click(form.getByRole('button', { name: 'Save' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals/g2')
    expect(JSON.parse(init.body)).toEqual({
      content: 'Pojechać do Japonii',
      layer: 'GOAL',
      horizon: 'FEW_MONTHS',
      due_date: null,
      category_code: null,
      completed: false,
      withdrawn: false,
    })
    expect(screen.queryByRole('form', { name: 'Edit entry' })).not.toBeInTheDocument()
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
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Edit' }))

    const form = editForm()
    await user.click(kind(form, 'Task'))
    setDate(form.getByLabelText('Due date'), '2026-09-01')
    await user.click(form.getByRole('button', { name: 'Save' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Przebiec półmaraton',
      layer: 'TASK',
      horizon: null,
      due_date: '2026-09-01',
      category_code: 'HEALTH',
      completed: false,
      withdrawn: false,
    })
  })

  it("drops a task's term when it becomes a long-term goal", async () => {
    stubApi([ELECTRICITY])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Zapłacić za prąd')).getByRole('button', { name: 'Edit' }))

    const form = editForm()
    expect(form.getByLabelText('Due date')).toHaveValue('2026-09-01')

    await user.click(kind(form, 'Goal'))
    await user.selectOptions(form.getByLabelText('Horizon'), 'FEW_MONTHS')
    await user.click(form.getByRole('button', { name: 'Save' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Zapłacić za prąd',
      layer: 'GOAL',
      horizon: 'FEW_MONTHS',
      due_date: null,
      category_code: 'HOME',
      completed: false,
      withdrawn: false,
    })
  })
})

/**
 * PUT is a full replace, so every edit resends the whole entry. These pin the fields the form has
 * to carry along untouched — each one is invisible in the UI and silently destroyed if the payload
 * drops it, which no amount of clicking through the happy path would reveal.
 */
describe('GoalsPage — an edit keeps the rest of the entry', () => {
  it('keeps a completed entry completed when only its text is edited', async () => {
    stubApi([COOKING])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Nauczyć się gotować')).getByRole('button', { name: 'Edit' }))

    const form = editForm()
    await user.clear(form.getByLabelText('Content'))
    await user.type(form.getByLabelText('Content'), 'Nauczyć się gotować (poprawka)')
    await user.click(form.getByRole('button', { name: 'Save' }))

    expect(JSON.parse(mutations()[0][1].body)).toMatchObject({
      content: 'Nauczyć się gotować (poprawka)',
      completed: true,
      withdrawn: false,
    })
  })

  it("carries a goal's horizon and category through a text-only edit", async () => {
    stubApi([RUN])
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Edit' }))

    const form = editForm()
    expect(form.getByLabelText('Horizon')).toHaveValue('THIS_YEAR')
    expect(form.getByLabelText('Category')).toHaveValue('HEALTH')

    await user.clear(form.getByLabelText('Content'))
    await user.type(form.getByLabelText('Content'), 'Przebiec maraton')
    await user.click(form.getByRole('button', { name: 'Save' }))

    expect(JSON.parse(mutations()[0][1].body)).toEqual({
      content: 'Przebiec maraton',
      layer: 'GOAL',
      horizon: 'THIS_YEAR',
      due_date: null,
      category_code: 'HEALTH',
      completed: false,
      withdrawn: false,
    })
  })
})

/** Answers the goals GET normally and fails every mutation with the given status. */
function stubFailingMutations(goals: Goal[], status: number, detail = 'boom') {
  fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
    const fixed = backdrop(url)
    if (fixed) return Promise.resolve(fixed)
    if ((init.method ?? 'GET') === 'GET') return Promise.resolve(response(200, { items: [...goals] }))
    return Promise.resolve(response(status, { detail }))
  })
}

function goalFetches() {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/goals')
}

describe('GoalsPage — a failed save', () => {
  it('says what is wrong when the server rejects the entry, not just "try again"', async () => {
    stubFailingMutations([RUN], 422)
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Complete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/rejected/i)
  })

  it('refetches when the entry is already gone, so the stale row disappears', async () => {
    stubFailingMutations([RUN], 404)
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Przebiec półmaraton')
    const before = goalFetches().length

    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Complete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer exists/i)
    expect(goalFetches().length).toBeGreaterThan(before)
  })

  /**
   * The refetch that answers a 404 can fail too. When it does, the row is still on screen, so
   * telling the user the list was refreshed is a claim the code cannot back up — and it used to
   * overwrite the banner that told the truth. Armed on the DELETE because that is the irreversible
   * one: the user needs to know whether what they see is current.
   */
  it('does not claim the list was refreshed when the refetch failed too', async () => {
    let refetch = false
    fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
      const fixed = backdrop(url)
      if (fixed) return Promise.resolve(fixed)
      if ((init.method ?? 'GET') === 'GET') {
        return Promise.resolve(refetch ? response(500, { detail: 'boom' }) : response(200, { items: [RUN] }))
      }
      refetch = true
      return Promise.resolve(response(404, { detail: 'No such goal' }))
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Delete' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/could not load/i)
    expect(alert).not.toHaveTextContent(/refreshed/i)
    expect(screen.getByText('Przebiec półmaraton')).toBeInTheDocument()
  })

  it('keeps what the user typed when the save fails', async () => {
    stubFailingMutations([], 500)
    const user = userEvent.setup()

    renderGoals()
    const form = await createForm()
    await user.type(form.getByLabelText('Content'), 'Przebiec półmaraton')
    await user.click(kind(form, 'Dream'))
    await user.click(form.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(form.getByLabelText('Content')).toHaveValue('Przebiec półmaraton')
  })

  it('records the failure, so a save that breaks in production is not invisible', async () => {
    const logged = vi.spyOn(console, 'error').mockImplementation(() => {})
    stubFailingMutations([RUN], 500)
    const user = userEvent.setup()

    renderGoals()
    await user.click((await item('Przebiec półmaraton')).getByRole('button', { name: 'Complete' }))

    await screen.findByRole('alert')
    expect(logged).toHaveBeenCalled()
  })
})

describe('GoalsPage — filters', () => {
  it('shows one layer at a time, heading and all, when narrowed by kind', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Przebiec półmaraton')
    await user.click(layerTabs().getByRole('button', { name: 'Task' }))

    expect(screen.getByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(screen.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()
    // The heading goes with its entries: an empty long-term section would read as "no goals".
    expect(screen.queryByRole('heading', { name: 'Long-term goals' })).not.toBeInTheDocument()
  })

  it('narrows by category across all three layers at once', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/goals?category=health')

    expect(await screen.findByText('Przebiec półmaraton')).toBeInTheDocument()
    expect(screen.queryByText('Zapłacić za prąd')).not.toBeInTheDocument()
    // An uncategorised entry is not a match for every category: picking one has to hide it, or the
    // NO_CATEGORY choice would be the only one that ever changes what a null-category entry does.
    expect(screen.queryByText('Pojechać do Japonii')).not.toBeInTheDocument()
    // Category narrows the list without touching the layer split — the sections all still render,
    // empty ones included. That is the deliberate asymmetry with the layer filter above.
    expect(screen.getByRole('heading', { name: 'Dreams' })).toBeInTheDocument()
  })

  /**
   * The category axis belongs to the rail and the withdrawn axis to its switch, so the page carries
   * neither control — and still obeys both, because all three axes live in one query string that
   * the page only ever reads.
   */
  it('leaves the other two axes to the rail while still reading them off the URL', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/goals?category=health')
    const page = within(screen.getByRole('main'))

    expect(await page.findByText('Przebiec półmaraton')).toBeInTheDocument()
    expect(page.queryByLabelText('Show category')).not.toBeInTheDocument()
    expect(page.queryByLabelText('Show withdrawn')).not.toBeInTheDocument()
  })

  /**
   * The tabs write into one query string shared with the rail's two axes, so the write has to merge
   * rather than replace. Nothing else in the suite writes a second filter, so without this the copy
   * could become a fresh `URLSearchParams` and every test would stay green while a category picked
   * in the rail silently cleared itself on the next layer press.
   */
  it('keeps the axes the rail owns when a layer is chosen', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])
    const user = userEvent.setup()

    renderGoals('/goals?category=home')
    await screen.findByText('Zapłacić za prąd')
    await user.click(layerTabs().getByRole('button', { name: 'Task' }))

    expect(chosenLayer()).toHaveTextContent('Task')
    expect(screen.getByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Dreams' })).not.toBeInTheDocument()
    // Lowercase in the link, SCREAMING_CASE on the wire: a URL is read, typed and shared by a
    // person. Reading is case-insensitive, so only this assertion can catch a write that is not.
    expect(screen.getByTestId('location')).toHaveTextContent('/goals?category=home&layer=task')
  })

  /** Clearing the layer takes the parameter out again rather than writing an empty one. */
  it('drops the layer from the URL when everything is asked for again', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])
    const user = userEvent.setup()

    renderGoals('/goals?layer=task')
    await screen.findByText('Zapłacić za prąd')
    await user.click(layerTabs().getByRole('button', { name: 'All' }))

    expect(screen.getByText('Przebiec półmaraton')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/goals')
    expect(screen.getByTestId('location')).not.toHaveTextContent('layer')
  })

  /**
   * `category_code` is nullable and the proposal engine treats null as one shared bucket, not as
   * eleven absences. Uncategorised entries therefore stay reachable under a choice of their own:
   * without it they are reachable only by clearing the filter, which is indistinguishable from
   * "there are none".
   */
  it('keeps uncategorised entries reachable, under an explicit choice of their own', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/goals?category=none')

    expect(await screen.findByText('Pojechać do Japonii')).toBeInTheDocument()
    expect(screen.queryByText('Przebiec półmaraton')).not.toBeInTheDocument()
    expect(screen.queryByText('Zapłacić za prąd')).not.toBeInTheDocument()
  })

  /**
   * The filters live in the query string, not in React state: a reload, a back button and a link
   * pasted to yourself all land on the same view for no code of ours.
   */
  it('reads both filters off the URL, so a reload or a shared link lands on the same view', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/goals?layer=task&category=home')

    expect(await screen.findByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Dreams' })).not.toBeInTheDocument()
    expect(chosenLayer()).toHaveTextContent('Task')
  })

  /**
   * A `layer` the app never wrote — a stale bookmark, a link from a later build — must not empty the
   * screen, and must not leave every tab unpressed over a full list either: a control that claims no
   * layer is chosen while one is being applied is the state that says "you have no entries" while
   * actively hiding them. Falling back to no filter is the only outcome the tabs can honestly show.
   */
  it('falls back to showing everything when the URL names a layer that does not exist', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/goals?layer=bogus')

    expect(await screen.findByText('Przebiec półmaraton')).toBeInTheDocument()
    expect(screen.getByText('Pojechać do Japonii')).toBeInTheDocument()
    expect(screen.getByText('Zapłacić za prąd')).toBeInTheDocument()
    expect(chosenLayer()).toHaveTextContent('All')
  })

  /**
   * `category` cannot be normalised the same way: its options come from the shell's fetched domains,
   * so an unresolved or failed `/api/categories` would throw away a perfectly valid `?category=home`
   * on every first paint. The message is what covers it — and it is the one signal that still tells
   * the truth when a stale code leaves the select reading as the no-filter option.
   */
  it('says nothing matched rather than showing an empty list as if there were no entries', async () => {
    stubApi([RUN, JAPAN, ELECTRICITY])

    renderGoals('/goals?layer=dream&category=home')

    expect(await screen.findByText(/No entry matches the filters/)).toBeInTheDocument()
    expect(screen.queryByText('Pojechać do Japonii')).not.toBeInTheDocument()
  })

  /** An empty list with no filter applied really is "you have no entries" — do not contradict it. */
  it('stays quiet about filters when the list is simply empty', async () => {
    stubApi([])

    renderGoals()

    expect(await screen.findByRole('form', { name: 'New entry' })).toBeInTheDocument()
    expect(screen.queryByText(/No entry matches the filters/)).not.toBeInTheDocument()
  })
})

/**
 * FR-013's "never" is reversible, which is the only reason it is a state rather than a delete. The
 * entries are hidden by default because a withdrawn entry is one the user asked not to be shown —
 * and reachable through the rail's switch, because a state you cannot get back out of is a delete
 * with extra steps.
 */
describe('GoalsPage — withdrawn entries', () => {
  it('hides withdrawn entries until the filter asks for them', async () => {
    stubApi([JAPAN, GUITAR])
    const user = userEvent.setup()

    renderGoals()
    await screen.findByText('Pojechać do Japonii')
    expect(screen.queryByText('Nauczyć się grać na gitarze')).not.toBeInTheDocument()

    await user.click(screen.getByLabelText('Show withdrawn'))

    expect(await screen.findByText('Nauczyć się grać na gitarze')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/goals?withdrawn=1')
  })

  /** Same rule as the other two axes: the view is in the URL, so a reload lands back on it. */
  it('reads the withdrawn filter off the URL', async () => {
    stubApi([JAPAN, GUITAR])

    renderGoals('/goals?withdrawn=1')

    expect(await screen.findByText('Nauczyć się grać na gitarze')).toBeInTheDocument()
    expect(screen.getByLabelText('Show withdrawn')).toBeChecked()
  })

  /**
   * Restore is the PUT the edit form already sends, with one flag flipped — no route of its own, and
   * therefore nothing new to keep in step with the full-replace payload.
   */
  it('restores a withdrawn entry through the same full-replace PUT', async () => {
    stubApi([GUITAR])
    const user = userEvent.setup()

    renderGoals('/goals?withdrawn=1')
    await user.click((await item('Nauczyć się grać na gitarze')).getByRole('button', { name: 'Restore' }))

    const [url, init] = mutations()[0]
    expect(url).toBe('/api/goals/g5')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual({
      content: 'Nauczyć się grać na gitarze',
      layer: 'DREAM',
      horizon: null,
      due_date: null,
      category_code: null,
      completed: false,
      withdrawn: false,
    })
  })

  /**
   * A withdrawn entry offers restore and delete, nothing else. Completing or editing one is asking
   * the user to act on an entry they have just said they will never act on — and restore is
   * already the complete toggle's own label for a completed entry, so offering both in one row
   * would put two identically named buttons side by side meaning different things.
   */
  it('offers a withdrawn entry only the actions that make sense for it', async () => {
    stubApi([GUITAR])

    renderGoals('/goals?withdrawn=1')
    const row = await item('Nauczyć się grać na gitarze')

    expect(row.getByRole('button', { name: 'Restore' })).toBeInTheDocument()
    expect(row.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Complete' })).not.toBeInTheDocument()
    expect(row.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })

  /**
   * The withdrawn filter is the one that is on by default, which makes it the one that can produce a
   * blank screen nobody asked for: a user whose only entry is withdrawn would see the empty state of
   * an account with no entries at all. It has to say something instead — that is the same lie the
   * load-failure banner a few lines up refuses to tell.
   */
  it('says entries are being hidden rather than showing the empty screen of a new account', async () => {
    stubApi([GUITAR])

    renderGoals()

    expect(await screen.findByText(/No entry matches the filters/)).toBeInTheDocument()
    expect(screen.queryByText('Nauczyć się grać na gitarze')).not.toBeInTheDocument()
  })
})

/**
 * US-01 is walked in English by every test above, because English is what the suite renders in.
 * This walks the same path in the other locale — add a dream, ask for a proposal — so the second
 * language is not left untested by the default having flipped. The entry text stays Polish either
 * way: it is what a person typed, not copy.
 */
describe('GoalsPage — the same path in Polish', () => {
  const WAITING = {
    id: 'p1',
    entry: JAPAN,
    neglected_days: 90,
    message: 'W sierpniu zapisałeś, że chcesz pojechać do Japonii. Minęły trzy miesiące.',
    source: 'LLM',
    answer: null,
    answered_at: null,
    first_step: null,
  } satisfies Proposal

  it('renders the shell, the form and the proposal in Polish, and asks the server for it too', async () => {
    await i18n.changeLanguage('pl')
    const stored: Goal[] = []
    fetchMock.mockImplementation((url: string, init: { method?: string } = {}) => {
      if (url === '/api/categories') return Promise.resolve(response(200, { items: DOMAINS }))
      if (url === '/api/proposals/pending') return Promise.resolve(response(204))
      if (url === '/api/proposals') return Promise.resolve(response(200, WAITING))
      if (url === '/api/goals' && (init.method ?? 'GET') === 'GET') {
        return Promise.resolve(response(200, { items: [...stored] }))
      }
      return Promise.resolve(response(200, {}))
    })
    const user = userEvent.setup()

    renderGoals()
    const form = within(await screen.findByRole('form', { name: 'Nowy wpis' }))
    await user.type(form.getByLabelText('Treść'), 'Pojechać do Japonii')
    await user.click(within(form.getByLabelText('Rodzaj')).getByRole('button', { name: 'Marzenie' }))
    stored.push(JAPAN)
    await user.click(form.getByRole('button', { name: 'Dodaj' }))

    expect(await section('Marzenia').findByText('Pojechać do Japonii')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Daj mi coś teraz' }))

    // The engine's prose is rendered verbatim; the four answers are the app's own copy.
    expect(await screen.findByText(WAITING.message)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Zaczynam' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nigdy' })).toBeInTheDocument()
    // The one place the Polish catalog has a plural table: 7, 30 and 90 all take the `many` form,
    // and only rendering them in Polish proves that form exists — a misspelled key would fall
    // through to `other` and read "Za 7 dnia" with every English test green.
    await user.click(screen.getByRole('button', { name: 'Przypomnij później' }))
    expect(screen.getByRole('button', { name: 'Za 7 dni' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Za 30 dni' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Za 90 dni' })).toBeInTheDocument()
    // And the wire carries the same choice, which is what makes the server's half follow.
    const headers = fetchMock.mock.calls.at(-1)?.[1].headers as Record<string, string>
    expect(headers['Accept-Language']).toBe('pl')
  })
})
