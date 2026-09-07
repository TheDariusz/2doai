import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext, useSearchParams } from 'react-router'
import { ApiError, api } from '../api/client'
import { ProposalCard } from './ProposalCard'
import { CheckIcon, PencilIcon, PlusIcon, TrashIcon } from './icons'
import { domainColor, type Domain } from '../layout/AppLayout'

/**
 * A task, a goal or a dream — one representation for all three layers, exactly as the `Goal` schema
 * in `openapi.yaml` defines it (snake_case straight off the wire).
 *
 * The five literals below are the wire contract, hardcoded here as they are in the spec and in the
 * backend enums. `GoalApiTest.publishesTheWireEnumsTheContractAnchors` parses the unions below and
 * set-compares them against the spec and the backend enums, so adding, removing or renaming a
 * literal on any side goes red — the guard lessons.md asks for whenever a literal spans the stack.
 * It parses rather than searches on purpose: each literal also appears in the label maps and in
 * `layer === 'GOAL'`, so widening these fields to `string` would leave a text search green.
 *
 * `horizon` and `due_date` are the two time fields, and the layer decides which one an entry may
 * carry: a GOAL always has a horizon and never a term, a DREAM has neither, a TASK may have a term
 * and never a horizon. The server answers 422 for any other combination.
 *
 * `remind_after` and `withdrawn_at` are what an answered proposal leaves on the entry (S-04b): the
 * date a quieted entry comes back, and the moment it was withdrawn. `withdrawn_at` has a second
 * writer — `GoalUpdate.withdrawn` on PUT, which is the only way back from a withdrawal — so it is
 * not proposal-only state. Both record *when* rather than a flag for the same reason `completed_at`
 * does, but they are not the same shape: `remind_after` is a plain date, because a snooze is "come
 * back on Thursday" rather than a moment in a timezone.
 */
export type Goal = {
  id: string
  content: string
  layer: 'GOAL' | 'DREAM' | 'TASK'
  horizon: 'THIS_YEAR' | 'FEW_MONTHS' | null
  due_date: string | null
  category_code: string | null
  completed_at: string | null
  remind_after: string | null
  withdrawn_at: string | null
  created_at: string
  updated_at: string
}

const HORIZONS = ['THIS_YEAR', 'FEW_MONTHS'] as const satisfies readonly NonNullable<Goal['horizon']>[]

/**
 * The three layers, in the order the screen shows them — the layer filter picks from this list, and
 * so do both `<select>`s. Tasks first: that is the layer which gives a reason to open the app on an
 * ordinary day. The labels live in the catalog under two different keys on purpose — `goals.layers`
 * names one entry, `goals.sections` names the group of them.
 */
const LAYERS = ['TASK', 'GOAL', 'DREAM'] as const satisfies readonly Goal['layer'][]

/**
 * The layer the URL is asking for, or `''` for "all of them". Shared by the page and the tabs so
 * they cannot disagree — and normalising is what keeps them honest: a layer the app never wrote (a
 * stale bookmark, a link from a later build) is no filter at all, because tabs showing nothing
 * pressed over a list that is being filtered is the one state that hides entries while claiming not
 * to. The value lives lowercased in the URL and uppercased on the wire, as `category` does: the
 * query string is a link a user reads and edits, the SCREAMING_CASE belongs to the enum behind it.
 */
function layerIn(params: URLSearchParams): '' | Goal['layer'] {
  const requested = (params.get('layer') ?? '').toUpperCase()
  return LAYERS.find((known) => known === requested) ?? ''
}

/**
 * The `?category=` value that asks for `category_code: null`. Not the empty string, because that is
 * already taken by "no filter at all" — and the distinction is the point: the proposal engine
 * treats null as one shared bucket, so uncategorised entries are a group a user can ask for, not
 * an absence to be hidden.
 */
const NO_CATEGORY = 'NONE'

/** What both forms send: `GoalCreation` in the spec, and `GoalUpdate` once `completed` is added. */
export type GoalDraft = Pick<Goal, 'content' | 'layer' | 'horizon' | 'due_date' | 'category_code'>

/**
 * What an entry can do to itself, handed down rather than re-derived per row: which row (if any)
 * is being edited, the one PUT that covers editing, converting, completing and un-completing, and
 * the DELETE that ends it.
 */
type ItemActions = {
  editing: string | null
  setEditing: (id: string | null) => void
  replace: (
    id: string,
    draft: GoalDraft,
    state: { completed: boolean; withdrawn: boolean },
  ) => Promise<boolean>
  remove: (id: string) => Promise<boolean>
}

/** The entry as the form sees it — PUT is a full replace, so untouched fields must be resent. */
function draftOf(goal: Goal): GoalDraft {
  return {
    content: goal.content,
    layer: goal.layer,
    horizon: goal.horizon,
    due_date: goal.due_date,
    category_code: goal.category_code,
  }
}

/**
 * Copy per failure, in the shape `AuthPage.messageFor` established. A single "try again" would be
 * wrong twice over: a 422 repeats identically however many times it is retried, and a missing CSRF
 * cookie needs a reload rather than a retry.
 */
function messageFor(status: number): GoalsError {
  if (status === 422) {
    // The form caps length and requires content, so this means the entry broke the layer × time
    // fields rule — the one validation a caller can hit without bypassing the form.
    return 'goals.errors.rejected'
  }
  if (status === 0) {
    // Never reached the server: the CSRF priming response has not landed. A reload primes it.
    return 'goals.errors.refresh'
  }
  return 'goals.errors.save'
}

/**
 * The banner is stored as a catalog key and translated at render, not as a translated string. Two
 * reasons: a banner raised before a language switch then follows the switch, and `load` does not
 * close over `t` — react-i18next hands out a new `t` per language, and a `load` that depended on it
 * would refetch every goal on every switch, a database query for content that is not localized.
 */
type GoalsError =
  | 'goals.errors.load'
  | 'goals.errors.gone'
  | 'goals.errors.rejected'
  | 'goals.errors.refresh'
  | 'goals.errors.save'

/** The whole S-02 + S-07 screen: all three layers, grouped, completed entries folded away. */
export function GoalsPage() {
  const { t } = useTranslation()
  const domains = useOutletContext<Domain[]>()
  // The filters live in the query string rather than in React state: a reload, a link pasted to
  // yourself and coming back to the screen from elsewhere all keep the view for free, and the
  // controls read from the URL rather than mirroring it, so there is one source of truth.
  const [params] = useSearchParams()
  const layer = layerIn(params)
  // `category` deliberately gets no `layerIn` guard of its own: its options are fetched, so
  // `domains` is still empty on the first paint and normalising would throw away a perfectly valid
  // `?category=home` on every load. A stale code is covered by the "nothing matched" message
  // instead. The rail writes this one; the page only ever reads it.
  const category = (params.get('category') ?? '').toUpperCase()
  // A third axis, and the only one that is on/off: a withdrawn entry is one the user asked not to be
  // shown, so it is hidden until this is set. Any value counts as "show them" — the rail's switch
  // writes exactly one, and an unknown one can only ever err towards showing the user their entries.
  const withdrawn = params.has('withdrawn')
  const [goals, setGoals] = useState<Goal[]>([])
  const [error, setError] = useState<GoalsError | null>(null)
  const [editing, setEditing] = useState<string | null>(null)

  const load = useCallback(
    () =>
      api<{ items: Goal[] }>('/goals').then(
        (page) => {
          setGoals(page.items)
          setError(null)
          return true
        },
        // A 401 already routes to /login via the session-expired event. Anything else would leave
        // an empty screen that looks like "you have no goals yet" — a lie worth avoiding. The
        // failure is bound and recorded because the copy is deliberately generic: without this,
        // a 500 and a parse bug are indistinguishable from the outside and leave no trace.
        (failure: unknown) => {
          console.error('goals: load failed', failure)
          setError('goals.errors.load')
          return false
        },
      ),
    [],
  )

  useEffect(() => {
    load()
  }, [load])

  /**
   * Every mutation is "send it, then refetch" — at single-user scale one extra GET is cheaper than
   * cache bookkeeping that can disagree with the server. Returns whether it landed, which is what
   * tells a form whether it may clear itself.
   *
   * A write that landed but whose refetch failed still returns `true`: the write is what the form
   * asks about, and answering `false` would keep a submitted create form populated and invite a
   * duplicate. The stale screen is reported separately, by `load`'s own banner.
   */
  async function save(request: Promise<unknown>): Promise<boolean> {
    try {
      await request
      await load()
      return true
    } catch (failure) {
      console.error('goals: save failed', failure)
      const status = failure instanceof ApiError ? failure.status : -1

      if (status === 404) {
        // The entry is gone — deleted from another tab, or the account erased elsewhere. Leaving
        // the stale row on screen with "try again" invites a retry that can only 404 again, so
        // refetch and let the list tell the truth. The message goes after the reload, which clears
        // it on success — and only if the reload succeeded: otherwise the row is still on screen
        // and `load`'s own "could not load" banner is the true one, so it must stand.
        if (await load()) {
          setError('goals.errors.gone')
        }
        return false
      }

      setError(messageFor(status))
      return false
    }
  }

  const actions: ItemActions = {
    editing,
    setEditing,
    // The two state flags travel together and by name: they are primitives on the server, so
    // dropping either is a 400 — and two adjacent booleans in a positional call are a swap waiting
    // to happen the day one more is added.
    replace: (id, draft, state) =>
      save(api(`/goals/${id}`, { method: 'PUT', body: { ...draft, ...state } })),
    remove: (id) => save(api(`/goals/${id}`, { method: 'DELETE' })),
  }

  // One predicate over both axes, so `visible.length` is exactly "does anything match the filters"
  // — the question the empty-state message below has to answer. `layer` still *also* picks which
  // sections render; `Section` re-filters by layer out of whatever list it is handed, so the extra
  // pass is idempotent rather than a second opinion that could disagree.
  const visible = goals.filter(
    (goal) =>
      (!category || (goal.category_code || NO_CATEGORY) === category) &&
      (!layer || goal.layer === layer) &&
      // Not `!withdrawn || …`: the filter *replaces* the default view rather than widening it, so
      // switching it on shows the withdrawn entries alone. Mixed into the ordinary list they would
      // be indistinguishable from live ones, which is the state the withdrawal was meant to end.
      Boolean(goal.withdrawn_at) === withdrawn,
  )

  const sectionProps = { goals: visible, domains, actions }

  return (
    <div className="goals">
      <div className="title-row">
        <h1>{t('goals.title')}</h1>
        <LayerTabs />
      </div>
      {error && <p role="alert">{t(error)}</p>}

      {/* Above the create form on purpose: FR-015 is the app asking the user a question, and it has
          to be the first thing on a screen whose whole point is that they had stopped looking.
          `load` is the refetch — every answer changes an entry, and so does saving a first step. */}
      <ProposalCard domains={domains} onChange={load} />

      <GoalForm
        name={t('goals.createForm')}
        submitLabel={t('goals.create')}
        domains={domains}
        onSubmit={(draft) => save(api('/goals', { method: 'POST', body: draft }))}
      />

      {/* An empty list under an active filter is not "you have no entries" — `load`'s failure path
          refuses that same lie a few lines up. It is also the last honest signal when a stale
          `?category=` matches no tag in the rail, so nothing on screen looks filtered at all.

          The condition is "there are entries, none of them showing" rather than a list of the
          filters that might be on, because one of them is *always* on: withdrawn entries are hidden
          by default, so a user whose only entry is withdrawn would otherwise get the blank screen
          this message exists to prevent. */}
      {visible.length === 0 && goals.length > 0 && <p role="status">{t('goals.noMatches')}</p>}

      {/* A filtered-out layer loses its heading along with its entries: an empty long-term
          section would read as "you have no goals". */}
      {LAYERS.filter((known) => !layer || known === layer).map((known) => (
        <Section key={known} layer={known} {...sectionProps} />
      ))}
    </div>
  )
}

/**
 * The one filter axis the screen still owns — the other two moved into the rail — reading and
 * writing the query string itself: `useSearchParams` is context-backed, so this call and the page's
 * see the same URL and there is nothing to pass down or keep in sync. The write merges rather than
 * replaces, or a layer press would silently clear the category the rail had set.
 *
 * A segmented group rather than a `<select>`: four options is a list short enough to *be* the
 * control, and the pressed tab says which layer is on without opening anything. The labels are the
 * per-entry layer names rather than the section headings, which are deliberately worded differently
 * — a layer names one entry, a heading names the group.
 */
function LayerTabs() {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()
  const chosen = layerIn(params)

  function choose(value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set('layer', value.toLowerCase())
    else next.delete('layer')
    setParams(next, { replace: true })
  }

  return (
    <div className="seg" role="group" aria-label={t('goals.filters.layer')}>
      <button type="button" aria-pressed={!chosen} onClick={() => choose('')}>
        {t('goals.filters.all')}
      </button>
      {LAYERS.map((value) => (
        <button
          key={value}
          type="button"
          aria-pressed={value === chosen}
          onClick={() => choose(value)}
        >
          {t(`goals.layers.${value}`)}
        </button>
      ))}
    </div>
  )
}

/**
 * The create form and the inline edit form are the same fields, so they are the same component —
 * `layer` is the only value held in React rather than read off the DOM at submit, because it decides
 * which time field is rendered at all: the horizon belongs to a goal, the term to a task, and an
 * entry carrying the other one is a 422 from the server. An invariant we can simply not offer.
 */
function GoalForm({
  name,
  submitLabel,
  goal,
  domains,
  onSubmit,
}: {
  name: string
  submitLabel: string
  goal?: Goal
  domains: Domain[]
  onSubmit: (draft: GoalDraft) => Promise<boolean>
}) {
  const { t } = useTranslation()
  const defaultLayer = goal?.layer ?? 'GOAL'
  const [layer, setLayer] = useState<Goal['layer']>(defaultLayer)
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)

    setPending(true)
    const saved = await onSubmit({
      content: String(data.get('content') ?? ''),
      layer,
      // Neither carries a layer guard: the control the layer does not own is never rendered, so it
      // is absent from the FormData and reads as null. PUT being a full replace is exactly why that
      // matters — a converted entry sends null for the field it just gave up, not its old value.
      horizon: data.get('horizon') as Goal['horizon'],
      // An untouched or unrendered date input reads as '', which is not a date the server accepts.
      due_date: String(data.get('due_date') ?? '') || null,
      category_code: String(data.get('category_code') ?? '') || null,
    })
    setPending(false)

    if (saved) {
      form.reset()
      // `reset()` restores the DOM defaults; `layer` is React state and has to follow.
      setLayer(defaultLayer)
    }
  }

  return (
    // The create form is the quick-add bar at the top of the screen; the edit form is the same
    // fields opened inside a row, so it drops the bar's own chrome and stacks when it has to.
    // `goal` is the discriminator either way — it is present only when an entry is being edited.
    <form className={goal ? 'entry-form' : 'entry-form quick-add'} aria-label={name} onSubmit={submit}>
      {!goal && <PlusIcon />}
      <label className="quick-content">
        {/* The placeholder names the three layers, but it cannot be the field's name: it disappears
            the moment anything is typed. The name stays, visually hidden. */}
        <span className="vh">{t('goals.content')}</span>
        {/* Mirrors Goal.MAX_CONTENT_LENGTH and the column width — same rule, stated client-side. */}
        <input
          name="content"
          required
          maxLength={500}
          placeholder={t('goals.placeholder')}
          defaultValue={goal?.content}
        />
      </label>
      {/* The same segmented control the layer tabs use, and for the same reason: three options that
          decide what the rest of the bar offers are worth seeing all at once. */}
      <div className="seg" role="group" aria-label={t('goals.layer')}>
        {LAYERS.map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={value === layer}
            onClick={() => setLayer(value)}
          >
            {t(`goals.layers.${value}`)}
          </button>
        ))}
      </div>
      {layer === 'GOAL' && (
        <label className="field">
          <span>{t('goals.horizon')}</span>
          <select name="horizon" required defaultValue={goal?.horizon ?? undefined}>
            {HORIZONS.map((value) => (
              <option key={value} value={value}>
                {t(`goals.horizons.${value}`)}
              </option>
            ))}
          </select>
        </label>
      )}
      {layer === 'TASK' && (
        <label className="field">
          <span>{t('goals.dueDate')}</span>
          {/* Native control, not a picker library — an ISO `YYYY-MM-DD` value for no bytes.
              Deliberately not `required`: most tasks have no deadline at all. */}
          <input type="date" name="due_date" defaultValue={goal?.due_date ?? undefined} />
        </label>
      )}
      <label className="field">
        <span>{t('goals.category')}</span>
        <select name="category_code" defaultValue={goal?.category_code ?? ''}>
          <option value="">{t('goals.noCategory')}</option>
          {domains.map((domain) => (
            <option key={domain.code} value={domain.code}>
              {domain.name}
            </option>
          ))}
        </select>
      </label>
      <button className="primary" type="submit" disabled={pending}>
        {submitLabel}
      </button>
    </form>
  )
}

function Section({
  layer,
  goals,
  domains,
  actions,
}: {
  layer: Goal['layer']
  goals: Goal[]
  domains: Domain[]
  actions: ItemActions
}) {
  const { t } = useTranslation()
  const mine = goals.filter((goal) => goal.layer === layer)
  const active = mine.filter((goal) => !goal.completed_at)
  const completed = mine.filter((goal) => goal.completed_at)

  return (
    <section>
      {/* The count is of the active entries alone: the completed ones are folded away below, and a
          heading that counted them would be a tally of a list nobody is looking at. */}
      <div className="section-head">
        <h2>{t(`goals.sections.${layer}`)}</h2>
        <small>{active.length}</small>
      </div>
      <ul>
        {active.map((goal) => (
          <Item key={goal.id} goal={goal} domains={domains} actions={actions} />
        ))}
      </ul>
      {completed.length > 0 && (
        // Native disclosure rather than a state toggle: closed by default, keyboard-accessible and
        // no JavaScript of ours involved.
        <details>
          <summary>{t('goals.completed', { n: completed.length })}</summary>
          <ul>
            {completed.map((goal) => (
              <Item key={goal.id} goal={goal} domains={domains} actions={actions} />
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

function Item({
  goal,
  domains,
  actions,
}: {
  goal: Goal
  domains: Domain[]
  actions: ItemActions
}) {
  const { t } = useTranslation()
  const done = Boolean(goal.completed_at)
  const withdrawn = Boolean(goal.withdrawn_at)

  if (actions.editing === goal.id) {
    return (
      <li className="row editing">
        <GoalForm
          name={t('goals.editForm')}
          submitLabel={t('goals.save')}
          goal={goal}
          domains={domains}
          onSubmit={async (draft) => {
            // Editing must not silently un-complete an entry, so its own state rides along.
            const saved = await actions.replace(goal.id, draft, { completed: done, withdrawn })
            if (saved) actions.setEditing(null)
            return saved
          }}
        />
        <button type="button" onClick={() => actions.setEditing(null)}>
          {t('goals.cancel')}
        </button>
      </li>
    )
  }

  // The wire carries the code; the label and the colour live in the shell data the outlet already
  // handed us, and the colour is the domain's *position* in it — the same derivation the rail's
  // tags use, so a row and its tag cannot end up two different shades of the same domain.
  const domain = domains.findIndex((known) => known.code === goal.category_code)

  return (
    <li className={withdrawn ? 'row dim' : 'row'}>
      {/*
        A withdrawn entry offers restore and delete, nothing else. Completing or editing one asks the
        user to act on an entry they have just said they never will — and restore is already the
        complete toggle's own label for a completed entry, so a withdrawn *and* completed entry
        would otherwise put two identically named buttons in one row meaning different things.
      */}
      {!withdrawn && (
        <button
          className={done ? 'check done' : 'check'}
          type="button"
          aria-label={done ? t('goals.restore') : t('goals.complete')}
          onClick={() => actions.replace(goal.id, draftOf(goal), { completed: !done, withdrawn })}
        >
          {done && <CheckIcon />}
        </button>
      )}
      <span className="content">{goal.content}</span>
      <span className="meta">
        {/* The term is the one chip drawn in its own colour: it is the only piece of an entry that
            can be late, and the rest are facts about the entry rather than about the clock. */}
        {goal.due_date && <span className="chip due">{t('goals.due', { date: goal.due_date })}</span>}
        {goal.horizon && <span className="chip">{t(`goals.horizons.${goal.horizon}`)}</span>}
        {domain >= 0 && (
          <span className="chip">
            <span className="dot" style={{ background: domainColor(domain) }} />
            {domains[domain].name}
          </span>
        )}
        {/* Only ever seen under the withdrawn filter, where every row carries it — but the switch is
            a control the user may have forgotten they flipped, and the row should say what it is. */}
        {withdrawn && <span className="chip">{t('goals.withdrawnTag')}</span>}
      </span>
      <span className="actions">
        {withdrawn ? (
          <button
            className="ghost"
            type="button"
            onClick={() => actions.replace(goal.id, draftOf(goal), { completed: done, withdrawn: false })}
          >
            {t('goals.restore')}
          </button>
        ) : (
          <button
            className="icon-btn"
            type="button"
            aria-label={t('goals.edit')}
            onClick={() => actions.setEditing(goal.id)}
          >
            <PencilIcon />
          </button>
        )}
        {/*
          Native `confirm` rather than a dialog of our own: blocking, focus-trapped and
          screen-reader-announced for free, for a single yes/no. A 404 needs no special handling —
          `save` already refetches when the entry turns out to be gone.
        */}
        <button
          className="icon-btn"
          type="button"
          aria-label={t('goals.delete')}
          onClick={() => {
            if (window.confirm(t('goals.confirmDelete', { content: goal.content }))) {
              actions.remove(goal.id)
            }
          }}
        >
          <TrashIcon />
        </button>
      </span>
    </li>
  )
}
