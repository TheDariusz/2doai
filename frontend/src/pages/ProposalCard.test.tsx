import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Goal } from './GoalsPage'
import { ProposalCard, type Proposal } from './ProposalCard'
import { response } from '../test/auth'

const GUITAR = {
  id: 'g1',
  content: 'Nauczyć się grać na gitarze',
  layer: 'DREAM',
  horizon: null,
  due_date: null,
  category_code: 'LEISURE',
  completed_at: null,
  remind_after: null,
  withdrawn_at: null,
  created_at: '2026-01-14T10:00:00Z',
  updated_at: '2026-01-14T10:00:00Z',
} satisfies Goal

const PROPOSAL = {
  id: 'p1',
  entry: GUITAR,
  neglected_days: 224,
  message: 'W styczniu zapisałeś, że chcesz nauczyć się grać na gitarze. Minęło osiem miesięcy.',
  source: 'LLM',
  answer: null,
  answered_at: null,
  first_step: null,
} satisfies Proposal

/** The card as the server returns it once answered — the same shape, three more fields filled. */
function answered(answer: Proposal['answer'], firstStep: string[] | null = null): Proposal {
  return { ...PROPOSAL, answer, answered_at: '2026-08-26T18:00:00Z', first_step: firstStep }
}

const fetchMock = vi.fn()
const onChange = vi.fn()

/** Answers the proposal POST with `first`, and every later call with `then`. */
function stubApi(first: ReturnType<typeof response>, then = response(200, answered('NOT_NOW'))) {
  let asked = false
  fetchMock.mockImplementation(() => {
    const next = asked ? then : first
    asked = true
    return Promise.resolve(next)
  })
}

function calls() {
  return fetchMock.mock.calls.map(([url, init]) => ({
    url,
    method: init?.method,
    body: init?.body ? JSON.parse(init.body) : undefined,
  }))
}

function renderCard() {
  render(<ProposalCard onChange={onChange} />)
  return userEvent.setup()
}

/** Press the button and wait for the card, which is what every answer test starts from. */
async function propose(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: 'Daj mi coś teraz' }))
  await screen.findByText(PROPOSAL.message)
}

beforeEach(() => {
  fetchMock.mockReset()
  onChange.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

describe('ProposalCard — proszenie o propozycję', () => {
  it('asks the engine for one entry and shows the prose it phrased', async () => {
    stubApi(response(200, PROPOSAL))
    const user = renderCard()

    await propose(user)

    expect(calls()[0]).toMatchObject({ url: '/api/proposals', method: 'POST' })
    // The prose is the proposal; the entry rides along so the user can see which one it means, and
    // the two are worth telling apart — the message paraphrases, the entry is verbatim.
    expect(screen.getByText(GUITAR.content)).toBeInTheDocument()
  })

  /**
   * 204 is "nothing is gathering dust", which is a real and good answer — the one state where an
   * empty card would read as a failure. It is also the answer a fresh account gets.
   */
  it('says nothing is waiting rather than rendering an empty card', async () => {
    stubApi(response(204))
    const user = renderCard()

    await user.click(screen.getByRole('button', { name: 'Daj mi coś teraz' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/nic (teraz )?nie/i)
    expect(screen.queryByRole('button', { name: 'Zaczynam' })).not.toBeInTheDocument()
  })

  it('shows the failure instead of an empty card when the engine cannot be reached', async () => {
    stubApi(response(500, { detail: 'boom' }))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const user = renderCard()

    await user.click(screen.getByRole('button', { name: 'Daj mi coś teraz' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/nie udało się/i)
    expect(screen.queryByRole('button', { name: 'Zaczynam' })).not.toBeInTheDocument()
  })

  /**
   * The model has a 60-second budget, so the wait is long enough that a user will press again — and
   * a second press mid-flight would open a second proposal the first press is about to replace.
   * The disabled button is the whole guard, which is why it is asserted while the call is in flight.
   */
  it('cannot be fired twice while the model is still thinking', async () => {
    let land: (value: unknown) => void = () => {}
    fetchMock.mockImplementation(() => new Promise((resolve) => (land = resolve)))
    const user = renderCard()

    await user.click(screen.getByRole('button', { name: 'Daj mi coś teraz' }))

    const button = screen.getByRole('button', { name: 'Daj mi coś teraz' })
    expect(button).toBeDisabled()
    expect(screen.getByRole('status')).toBeInTheDocument()

    land(response(200, PROPOSAL))
    await screen.findByText(PROPOSAL.message)
    expect(button).toBeEnabled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('ProposalCard — cztery odpowiedzi', () => {
  it('sends "zaczynam" and nothing else', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('STARTING', ['Wypożycz gitarę'])))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Zaczynam' }))

    await waitFor(() => expect(calls()).toHaveLength(2))
    expect(calls()[1]).toEqual({
      url: '/api/proposals/p1/answer',
      method: 'POST',
      body: { answer: 'STARTING' },
    })
  })

  it('sends "nie teraz" without a term, because the user named none', async () => {
    stubApi(response(200, PROPOSAL))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Nie teraz' }))

    await waitFor(() => expect(calls()).toHaveLength(2))
    // A term beside any answer but REMIND_LATER is a 422: the server refuses to silently drop it.
    expect(calls()[1].body).toEqual({ answer: 'NOT_NOW' })
  })

  /**
   * The three presets are a second step rather than three more buttons in the row: four answers plus
   * three terms is seven controls to read before answering a question the app asked.
   */
  it('asks which term before sending "przypomnij później"', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('REMIND_LATER')))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Przypomnij później' }))

    expect(calls()).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Za 30 dni' }))

    await waitFor(() => expect(calls()).toHaveLength(2))
    expect(calls()[1].body).toEqual({ answer: 'REMIND_LATER', remind_in_days: 30 })
  })

  it('offers exactly the three terms the server accepts', async () => {
    stubApi(response(200, PROPOSAL))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Przypomnij później' }))

    // 7/30/90 and nothing else — anything the server does not know is a 422 the user cannot fix.
    expect(screen.getByRole('button', { name: 'Za 7 dni' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Za 30 dni' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Za 90 dni' })).toBeInTheDocument()
  })

  it('sends "nigdy" and says where the entry went', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('NEVER')))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Nigdy' }))

    await waitFor(() => expect(calls()).toHaveLength(2))
    expect(calls()[1].body).toEqual({ answer: 'NEVER' })
    // Withdrawal is reversible, and the filter is the only way back — saying so is the difference
    // between a reversible state and a delete the user thinks they just performed.
    expect(await screen.findByText(/wycofan/i)).toBeInTheDocument()
  })

  /**
   * All four change the entry list — three write `remind_after`, the fourth `withdrawn_at` — and the
   * page below is showing that list. Without this the screen keeps rendering pre-answer rows.
   */
  it('tells the page to refetch, because every answer changes an entry', async () => {
    stubApi(response(200, PROPOSAL))
    const user = renderCard()

    await propose(user)
    expect(onChange).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Nie teraz' }))

    await waitFor(() => expect(onChange).toHaveBeenCalled())
  })

  it('keeps the card and says so when the answer does not land', async () => {
    stubApi(response(200, PROPOSAL), response(500, { detail: 'boom' }))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Nie teraz' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/nie udało się/i)
    // The answer can be retried only if the buttons are still there.
    expect(screen.getByRole('button', { name: 'Nie teraz' })).toBeEnabled()
  })
})

describe('ProposalCard — pierwszy krok', () => {
  const STEPS = ['Wypożycz gitarę na miesiąc', 'Znajdź nauczyciela w okolicy', 'Zagraj jeden akord']

  it('renders the bullets the model came back with', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('STARTING', STEPS)))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Zaczynam' }))

    for (const step of STEPS) {
      expect(await screen.findByText(step)).toBeInTheDocument()
    }
  })

  /** FR-014's point: a bullet is worth something only if it can become a task in one click. */
  it('saves a bullet as a current task in the entry’s own category', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('STARTING', STEPS)))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Zaczynam' }))

    const row = within((await screen.findByText(STEPS[1])).closest('li') as HTMLElement)
    await user.click(row.getByRole('button', { name: 'Zapisz jako zadanie' }))

    await waitFor(() => expect(calls()).toHaveLength(3))
    expect(calls()[2]).toEqual({
      url: '/api/goals',
      method: 'POST',
      body: {
        content: STEPS[1],
        layer: 'TASK',
        horizon: null,
        // A first step is for now, not for a date the app invented on the user's behalf.
        due_date: null,
        // The step belongs to the same part of life the entry does — the one field the card can
        // fill honestly, and the one that decides which domain the task shows up under.
        category_code: 'LEISURE',
      },
    })
  })

  /** A saved bullet must stop offering to be saved, or one click becomes three identical tasks. */
  it('will not save the same bullet twice', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('STARTING', STEPS)))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Zaczynam' }))
    const row = within((await screen.findByText(STEPS[0])).closest('li') as HTMLElement)
    await user.click(row.getByRole('button', { name: 'Zapisz jako zadanie' }))

    await waitFor(() => expect(row.queryByRole('button', { name: 'Zapisz jako zadanie' })).toBeNull())
    expect(row.getByText(/zapisano/i)).toBeInTheDocument()
  })

  /**
   * The server answers 200 with no bullets when the model call fails — the answer landed, the plan
   * did not. Silence there reads as "starting means nothing happens".
   */
  it('says the plan is missing when the model did not manage one', async () => {
    stubApi(response(200, PROPOSAL), response(200, answered('STARTING', [])))
    const user = renderCard()

    await propose(user)
    await user.click(screen.getByRole('button', { name: 'Zaczynam' }))

    expect(await screen.findByText(/nie udało się przygotować/i)).toBeInTheDocument()
  })
})
