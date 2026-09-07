import { useEffect, useState } from 'react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { ApiError, api } from '../api/client'
import { SparkleIcon } from './icons'
import { domainColor, type Domain } from '../layout/AppLayout'
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
export type UserAnswer = 'STARTING' | 'NOT_NOW' | 'REMIND_LATER' | 'NEVER'

export type Proposal = {
  id: string
  entry: Goal
  neglected_days: number
  message: string
  source: 'LLM' | 'TEMPLATE'
  /**
   * Only ever one of the four the user can give, or null. The enum has a fifth, `SUPERSEDED`, which
   * the *server* writes when the natural rhythm replaces a proposal nobody answered — but it cannot
   * reach this card: a superseded proposal is by definition no longer pending, and the card only
   * ever holds a pending one or the one just answered here. Narrowing to `UserAnswer` here is what
   * lets the confirmation copy be a total mapping instead of a guarded one.
   */
  answer: UserAnswer | null
  answered_at: string | null
  first_step: string[] | null
}

/**
 * The terms FR-013 offers, and the only ones the server accepts — anything else is a 422 the user
 * has no way to fix. Mirrors `ProposalAnswerRequest.REMIND_PRESETS`.
 */
const TERMS = [7, 30, 90]

/** The three calls the card makes, and the failure sentence each one has in the catalog. */
type Attempt = 'propose' | 'answer' | 'saveStep'

/**
 * Copy per failure, in the shape `GoalsPage.messageFor` and `AuthPage.messageFor` established.
 *
 * The generic case is a whole sentence per call rather than one template holding a fragment: that
 * shape composed its sentence out of grammar, and a fragment declined to fit one language does not
 * fit the next one.
 */
function messageFor(t: TFunction, status: number, failed: Attempt): string {
  if (status === 0) {
    // Never reached the server: the CSRF priming response has not landed. A reload primes it.
    return t('proposal.errors.refresh')
  }
  if (status === 409) {
    // A proposal can be answered exactly once, and a retry can only 409 again — asking for a new
    // proposal is the way forward, so say that rather than "try again".
    return t('proposal.errors.alreadyAnswered')
  }
  return t(`proposal.errors.${failed}`)
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
 *
 * <p>`domains` is handed down for the same reason the rows get it: the entry carries a
 * `category_code` and the user reads a name and a colour. Passed rather than read off the outlet, so
 * the card stays renderable outside the shell's route.
 */
export function ProposalCard({ domains, onChange }: { domains: Domain[]; onChange: () => void }) {
  const { t } = useTranslation()
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
   * FR-018's other channel. The natural rhythm opens a proposal on its own and emails it, so the one
   * already waiting has to be on screen the moment the app opens — the user pressed nothing to get
   * here, and the email is a nudge, not the delivery.
   *
   * Deliberately not routed through `attempt`: nobody asked for this read, so it must not disable
   * their button, must not claim to be searching, and must not raise a banner about a failure they
   * did not cause and cannot act on — the button is still there and still works. Recorded in the
   * console, which is where a background failure belongs.
   *
   * Safe against a press that lands first: 204 sets nothing, and a 200 can only be the very
   * proposal `propose()` hands back anyway — a pending one short-circuits it.
   */
  useEffect(() => {
    api<Proposal | undefined>('/proposals/pending')
      .then((waiting) => {
        if (waiting) setProposal(waiting)
      })
      .catch((failure) => console.error('proposal: reading the pending slot failed', failure))
  }, [])

  /**
   * Every call shares this: report what failed, record it, and never leave the card half-built.
   * Returns the response rather than a bare boolean, so a caller narrows on `ok` instead of
   * smuggling the value out of a `.then` into a variable TypeScript cannot prove was assigned.
   */
  async function attempt<T>(request: Promise<T>, what: Attempt): Promise<{ ok: true; value: T } | { ok: false }> {
    setPending(true)
    setError(null)
    try {
      return { ok: true, value: await request }
    } catch (failure) {
      // Bound and recorded because the copy is generic: without this a 500 and a parse bug are
      // indistinguishable from the outside and leave no trace.
      console.error(`proposal: ${what} failed`, failure)
      setError(messageFor(t, failure instanceof ApiError ? failure.status : -1, what))
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
    const asked = await attempt(api<Proposal | undefined>('/proposals', { method: 'POST' }), 'propose')
    if (!asked.ok) return
    setProposal(asked.value ?? null)
    setNothingWaiting(!asked.value)
  }

  async function answer(value: UserAnswer, remindInDays?: number) {
    if (!proposal) return
    // Absent, not null: three of the four answers legitimately carry no term, and a term sent
    // beside any of them is a 422 rather than a value the server quietly drops.
    const body = remindInDays === undefined ? { answer: value } : { answer: value, remind_in_days: remindInDays }
    const landed = await attempt(
      api<Proposal>(`/proposals/${proposal.id}/answer`, { method: 'POST', body }),
      'answer',
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
    const landed = await attempt(api('/goals', { method: 'POST', body: draft }), 'saveStep')
    if (!landed.ok) return
    setSaved((all) => [...all, at])
    onChange()
  }

  return (
    <section className="proposal" aria-label={t('proposal.label')}>
      <div className="proposal-head">
        {/* The card is the only thing on the screen the user did not write, so it says whose voice
            it is before it says anything else. */}
        <p className="eyebrow">
          <SparkleIcon />
          {t('proposal.label')}
        </p>
        {/* Disabled while in flight, and that is the whole double-fire guard: the model has a
            60-second budget, so the wait is long enough that a user will press again — and a second
            press would open a second proposal the first is about to hand back anyway. */}
        <button className="soft" type="button" onClick={propose} disabled={pending}>
          {t('proposal.ask')}
        </button>
      </div>

      {pending && <p role="status">{t('proposal.searching')}</p>}
      {error && <p role="alert">{error}</p>}
      {nothingWaiting && <p role="status">{t('proposal.nothingWaiting')}</p>}

      {proposal && (
        <article>
          <p className="proposal-message">{proposal.message}</p>
          {/* The entry verbatim beside the prose that paraphrases it: the message is the engine
              talking, this is which entry it means — in the same chips the rows use, so the two
              read as the same entry. */}
          <Entry entry={proposal.entry} domains={domains} />

          {proposal.answer ? (
            <>
              <p role="status">{t(`proposal.confirmation.${proposal.answer}`)}</p>
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
 * Which entry the proposal is about: the domain's dot and name, and whichever time field the layer
 * owns. The same chips a row carries, deliberately — the user has to recognise the entry they are
 * being asked about from the list they already scrolled past.
 */
function Entry({ entry, domains }: { entry: Goal; domains: Domain[] }) {
  const { t } = useTranslation()
  const domain = domains.findIndex((known) => known.code === entry.category_code)

  return (
    <p className="proposal-entry">
      {domain >= 0 && <span className="dot" style={{ background: domainColor(domain) }} />}
      <span className="content">{entry.content}</span>
      {domain >= 0 && <span className="chip">{domains[domain].name}</span>}
      {entry.horizon && <span className="chip">{t(`goals.horizons.${entry.horizon}`)}</span>}
      {entry.due_date && <span className="chip due">{t('goals.due', { date: entry.due_date })}</span>}
    </p>
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
  answer: (value: UserAnswer, remindInDays?: number) => void
}) {
  const { t } = useTranslation()

  if (askingTerm) {
    return (
      <div className="answers">
        {TERMS.map((days) => (
          <button key={days} type="button" disabled={pending} onClick={() => answer('REMIND_LATER', days)}>
            {/* A plural key rather than a number dropped into a fixed phrase: 7, 30 and 90 happen
                to share one Polish form, and a one-day preset would have broken it silently. */}
            {t('proposal.remindIn', { count: days })}
          </button>
        ))}
      </div>
    )
  }

  return (
    // Drawn in three weights, in the order FR-013 asks them: starting is what the card is for and
    // is the only accented one, the two "later" answers are ordinary, and withdrawing an entry is
    // the quietest — it is reversible, but nothing should invite it.
    <div className="answers">
      <button className="primary" type="button" disabled={pending} onClick={() => answer('STARTING')}>
        {t('proposal.answers.STARTING')}
      </button>
      <button type="button" disabled={pending} onClick={() => answer('NOT_NOW')}>
        {t('proposal.answers.NOT_NOW')}
      </button>
      <button type="button" disabled={pending} onClick={askTerm}>
        {t('proposal.answers.REMIND_LATER')}
      </button>
      <button className="ghost" type="button" disabled={pending} onClick={() => answer('NEVER')}>
        {t('proposal.answers.NEVER')}
      </button>
    </div>
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
  const { t } = useTranslation()

  if (steps.length === 0) {
    // No "try again": the answer is recorded, so the button is gone and a second press would 409.
    // The bullets were the extra, and this is the one honest thing left to say about them.
    return <p>{t('proposal.noFirstStep')}</p>
  }

  return (
    <ul>
      {steps.map((step, at) => (
        <li key={at}>
          <p>{step}</p>
          {/* Saved bullets stop offering to be saved: one enthusiastic click otherwise becomes
              three identical tasks, and the list they land in is a screen away. */}
          {saved.includes(at) ? (
            <small>{t('proposal.stepSaved')}</small>
          ) : (
            <button type="button" disabled={pending} onClick={() => save(step, at)}>
              {t('proposal.saveStep')}
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
