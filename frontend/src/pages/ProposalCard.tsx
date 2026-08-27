import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Goal, GoalDraft } from './GoalsPage'

/**
 * What the engine came back with, exactly as the `Proposal` schema in `openapi.yaml` defines it
 * (snake_case straight off the wire, like `Goal`).
 *
 * `answer`, `answered_at` and `first_step` are null until the user answers, and the answer endpoint
 * returns this same shape with them filled — which is why one state holds both: the card renders the
 * proposal it has, whether it just asked for one or just answered one.
 *
 * `first_step` distinguishes null from empty and the card renders them differently: null is "not a
 * STARTING answer", empty is "the answer landed but the model did not". `source` is not rendered —
 * it exists so a demo can tell a real Sonnet proposal from the template fallback in the response.
 */
export type Proposal = {
  id: string
  entry: Goal
  neglected_days: number
  message: string
  source: 'LLM' | 'TEMPLATE'
  answer: 'STARTING' | 'NOT_NOW' | 'REMIND_LATER' | 'NEVER' | null
  answered_at: string | null
  first_step: string[] | null
}

/**
 * The terms FR-013 offers, and the only ones the server accepts — anything else is a 422 the user
 * has no way to fix. Mirrors `ProposalAnswerRequest.REMIND_PRESETS`.
 */
const TERMS = [7, 30, 90]

/** What the user is told happened, per answer. STARTING's is the bullets, rendered below instead. */
const CONFIRMATION: Record<NonNullable<Proposal['answer']>, string> = {
  STARTING: 'Pierwszy krok:',
  NOT_NOW: 'Dobrze — wrócimy do tego za kilka dni.',
  REMIND_LATER: 'Przypomnimy w wybranym terminie.',
  // Withdrawal is reversible and the filter is the only way back to it, so the copy has to say
  // where the entry went — otherwise NEVER reads as a delete the user just performed by accident.
  NEVER: 'Wycofane — wpis znajdziesz pod filtrem „Pokaż wycofane”.',
}

/** Copy per failure, in the shape `GoalsPage.messageFor` and `AuthPage.messageFor` established. */
function messageFor(status: number, what: string): string {
  if (status === 0) {
    // Never reached the server: the CSRF priming response has not landed. A reload primes it.
    return 'Odśwież stronę i spróbuj ponownie.'
  }
  if (status === 409) {
    // A proposal can be answered exactly once, and a retry can only 409 again — asking for a new
    // proposal is the way forward, so say that rather than "try again".
    return 'Ta propozycja została już rozstrzygnięta — poproś o nową.'
  }
  return `Nie udało się ${what}. Spróbuj ponownie.`
}

/**
 * FR-015's "give me something now": the button, the proposal it returns, the four answers of
 * FR-013 and FR-014's first step.
 *
 * <p>A file of its own rather than a section of `GoalsPage`, which is long enough already and owns a
 * different thing — this is one self-contained flow that happens to sit above an entry list. It
 * takes the page's refetch as `onChange` rather than the list itself: every answer changes an entry
 * (three write `remind_after`, the fourth `withdrawn_at`) and so does saving a bullet, but the card
 * never needs to read what is in the list.
 */
export function ProposalCard({ onChange }: { onChange: () => void }) {
  const [proposal, setProposal] = useState<Proposal | null>(null)
  // Separate from `proposal === null`, which is also the state before the button is ever pressed:
  // only one of the two is worth a message, and rendering it in the other is a lie about an account
  // nobody has asked anything about yet.
  const [nothingWaiting, setNothingWaiting] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [askingTerm, setAskingTerm] = useState(false)
  // Positions, not the bullets themselves: a model can return the same sentence twice, and keying
  // this by text would mark both saved on one click — and give React two <li> with one key.
  const [saved, setSaved] = useState<number[]>([])

  /**
   * Every call shares this: report what failed, record it, and never leave the card half-built.
   * Returns the response rather than a bare boolean, so a caller narrows on `ok` instead of
   * smuggling the value out of a `.then` into a variable TypeScript cannot prove was assigned.
   */
  async function attempt<T>(request: Promise<T>, what: string): Promise<{ ok: true; value: T } | { ok: false }> {
    setPending(true)
    setError(null)
    try {
      return { ok: true, value: await request }
    } catch (failure) {
      // Bound and recorded because the copy is generic: without this a 500 and a parse bug are
      // indistinguishable from the outside and leave no trace.
      console.error(`proposal: ${what} failed`, failure)
      setError(messageFor(failure instanceof ApiError ? failure.status : -1, what))
      return { ok: false }
    } finally {
      setPending(false)
    }
  }

  async function propose() {
    setNothingWaiting(false)
    setAskingTerm(false)
    setSaved([])
    // 204 is a legitimate answer — nothing is gathering dust — and `api` returns undefined for it.
    const asked = await attempt(api<Proposal | undefined>('/proposals', { method: 'POST' }), 'pobrać propozycji')
    if (!asked.ok) return
    setProposal(asked.value ?? null)
    setNothingWaiting(!asked.value)
  }

  async function answer(value: NonNullable<Proposal['answer']>, remindInDays?: number) {
    if (!proposal) return
    // Absent, not null: three of the four answers legitimately carry no term, and a term sent
    // beside any of them is a 422 rather than a value the server quietly drops.
    const body = remindInDays === undefined ? { answer: value } : { answer: value, remind_in_days: remindInDays }
    const landed = await attempt(
      api<Proposal>(`/proposals/${proposal.id}/answer`, { method: 'POST', body }),
      'zapisać odpowiedzi',
    )
    if (!landed.ok) return
    // No `setAskingTerm(false)`: the answered proposal unmounts `Answers` outright, and `propose()`
    // is the only route back to a pending one — it already resets the flag.
    setProposal(landed.value)
    onChange()
  }

  async function saveStep(step: string, at: number) {
    // Typed as the page's `GoalDraft` rather than an inline literal: this is the goals contract, and
    // a field added to it should break here at compile time rather than at the server's 400.
    const draft: GoalDraft = {
      content: step,
      // A first step is a thing to do now, which is what the task layer is for — and it has no
      // date, because the app has no business inventing a deadline the user did not name.
      layer: 'TASK',
      horizon: null,
      due_date: null,
      // The one field the card can fill honestly: the step belongs to the same part of life the
      // entry does, and the category is what decides which domain it shows up under.
      category_code: proposal?.entry.category_code ?? null,
    }
    const landed = await attempt(api('/goals', { method: 'POST', body: draft }), 'zapisać zadania')
    if (!landed.ok) return
    setSaved((all) => [...all, at])
    onChange()
  }

  return (
    <section className="proposal" aria-label="Propozycja">
      {/* Disabled while in flight, and that is the whole double-fire guard: the model has a
          60-second budget, so the wait is long enough that a user will press again — and a second
          press would open a second proposal the first is about to hand back anyway. */}
      <button type="button" onClick={propose} disabled={pending}>
        Daj mi coś teraz
      </button>

      {pending && <p role="status">Szukam wpisu, do którego warto wrócić…</p>}
      {error && <p role="alert">{error}</p>}
      {nothingWaiting && <p role="status">Nic teraz nie czeka — nic nie leży odłogiem.</p>}

      {proposal && (
        <article>
          <p>{proposal.message}</p>
          {/* The entry verbatim beside the prose that paraphrases it: the message is the engine
              talking, this is which entry it means. */}
          <small>{proposal.entry.content}</small>

          {proposal.answer ? (
            <>
              <p role="status">{CONFIRMATION[proposal.answer]}</p>
              {proposal.first_step && (
                <FirstStep steps={proposal.first_step} saved={saved} save={saveStep} pending={pending} />
              )}
            </>
          ) : (
            <Answers
              pending={pending}
              askingTerm={askingTerm}
              askTerm={() => setAskingTerm(true)}
              answer={answer}
            />
          )}
        </article>
      )}
    </section>
  )
}

/**
 * The four responses, with the three terms as a second step rather than three more buttons in the
 * row — four answers plus three terms is seven controls to read before answering a question the app
 * asked, and six of them are the same answer.
 */
function Answers({
  pending,
  askingTerm,
  askTerm,
  answer,
}: {
  pending: boolean
  askingTerm: boolean
  askTerm: () => void
  answer: (value: NonNullable<Proposal['answer']>, remindInDays?: number) => void
}) {
  if (askingTerm) {
    return (
      <p>
        {TERMS.map((days) => (
          <button key={days} type="button" disabled={pending} onClick={() => answer('REMIND_LATER', days)}>
            Za {days} dni
          </button>
        ))}
      </p>
    )
  }

  return (
    <p>
      <button type="button" disabled={pending} onClick={() => answer('STARTING')}>
        Zaczynam
      </button>
      <button type="button" disabled={pending} onClick={() => answer('NOT_NOW')}>
        Nie teraz
      </button>
      <button type="button" disabled={pending} onClick={askTerm}>
        Przypomnij później
      </button>
      <button type="button" disabled={pending} onClick={() => answer('NEVER')}>
        Nigdy
      </button>
    </p>
  )
}

/**
 * FR-014's bullets. An empty list is not the same as no list: the server answers 200 with no bullets
 * when the model call fails, so the answer landed and the plan did not — and silence there would
 * read as "STARTING means nothing happens".
 */
function FirstStep({
  steps,
  saved,
  save,
  pending,
}: {
  steps: string[]
  saved: number[]
  save: (step: string, at: number) => void
  pending: boolean
}) {
  if (steps.length === 0) {
    // No "try again": the answer is recorded, so the button is gone and a second press would 409.
    // The bullets were the extra, and this is the one honest thing left to say about them.
    return <p>Odpowiedź zapisana, ale nie udało się przygotować pierwszego kroku.</p>
  }

  return (
    <ul>
      {steps.map((step, at) => (
        <li key={at}>
          <p>{step}</p>
          {/* Saved bullets stop offering to be saved: one enthusiastic click otherwise becomes
              three identical tasks, and the list they land in is a screen away. */}
          {saved.includes(at) ? (
            <small>Zapisano</small>
          ) : (
            <button type="button" disabled={pending} onClick={() => save(step, at)}>
              Zapisz jako zadanie
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
