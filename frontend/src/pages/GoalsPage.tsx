import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useOutletContext } from 'react-router'
import { api } from '../api/client'
import type { Domain } from '../layout/AppLayout'

/**
 * A goal or a dream — one representation for both layers, exactly as the `Goal` schema in
 * `openapi.yaml` defines it (snake_case straight off the wire).
 *
 * The four literals below are the wire contract, hardcoded here as they are in the spec and in the
 * backend enums. `GoalApiTest.publishesTheWireEnumsTheContractAnchors` reads all three copies and
 * fails if any one is renamed — the guard lessons.md asks for whenever a literal spans the stack.
 */
export type Goal = {
  id: string
  content: string
  layer: 'GOAL' | 'DREAM'
  horizon: 'THIS_YEAR' | 'FEW_MONTHS' | null
  category_code: string | null
  completed_at: string | null
  created_at: string
  updated_at: string
}

const HORIZON_LABEL: Record<NonNullable<Goal['horizon']>, string> = {
  THIS_YEAR: 'W tym roku',
  FEW_MONTHS: 'Najbliższe miesiące',
}

const LAYER_LABEL: Record<Goal['layer'], string> = {
  GOAL: 'Cel',
  DREAM: 'Marzenie',
}

/** What both forms send: `GoalCreation` in the spec, and `GoalUpdate` once `completed` is added. */
type GoalDraft = Pick<Goal, 'content' | 'layer' | 'horizon' | 'category_code'>

/**
 * What an entry can do to itself, handed down rather than re-derived per row: which row (if any)
 * is being edited, and the one PUT that covers editing, converting, completing and un-completing.
 */
type ItemActions = {
  editing: string | null
  setEditing: (id: string | null) => void
  replace: (id: string, draft: GoalDraft, completed: boolean) => Promise<boolean>
}

/** The entry as the form sees it — PUT is a full replace, so untouched fields must be resent. */
function draftOf(goal: Goal): GoalDraft {
  return {
    content: goal.content,
    layer: goal.layer,
    horizon: goal.horizon,
    category_code: goal.category_code,
  }
}

/** The whole S-02 screen: both layers, grouped, with completed entries folded away. */
export function GoalsPage() {
  const domains = useOutletContext<Domain[]>()
  const [goals, setGoals] = useState<Goal[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<string | null>(null)

  const load = useCallback(
    () =>
      api<{ items: Goal[] }>('/goals').then(
        (page) => {
          setGoals(page.items)
          setError(null)
        },
        // A 401 already routes to /login via the session-expired event. Anything else would leave
        // an empty screen that looks like "you have no goals yet" — a lie worth avoiding.
        () => setError('Nie udało się wczytać celów — odśwież stronę.'),
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
   */
  async function save(request: Promise<unknown>): Promise<boolean> {
    try {
      await request
      await load()
      return true
    } catch {
      setError('Nie udało się zapisać zmiany. Spróbuj ponownie.')
      return false
    }
  }

  const actions: ItemActions = {
    editing,
    setEditing,
    replace: (id, draft, completed) =>
      save(api(`/goals/${id}`, { method: 'PUT', body: { ...draft, completed } })),
  }

  return (
    <div className="goals">
      <h1>Cele i marzenia</h1>
      {error && <p role="alert">{error}</p>}

      <GoalForm
        name="Nowy cel lub marzenie"
        submitLabel="Dodaj"
        domains={domains}
        onSubmit={(draft) => save(api('/goals', { method: 'POST', body: draft }))}
      />

      <Section
        title="Cele długoterminowe"
        layer="GOAL"
        goals={goals}
        domains={domains}
        actions={actions}
      />
      <Section title="Marzenia" layer="DREAM" goals={goals} domains={domains} actions={actions} />
    </div>
  )
}

/**
 * The create form and the inline edit form are the same four fields, so they are the same
 * component — `layer` is the one piece of state, because the horizon field exists only for a goal
 * (a dream carrying one is a 422 from the server, and an invariant we can simply not offer).
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
      // Not merely hidden — omitted from the payload, so a layer switch cannot smuggle the old
      // horizon along and trip the cross-field rule.
      horizon: layer === 'GOAL' ? (data.get('horizon') as Goal['horizon']) : null,
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
    <form aria-label={name} onSubmit={submit}>
      <label>
        Treść
        {/* Mirrors Goal.MAX_CONTENT_LENGTH and the column width — same rule, stated client-side. */}
        <input name="content" required maxLength={500} defaultValue={goal?.content} />
      </label>
      <label>
        Rodzaj
        <select
          name="layer"
          value={layer}
          onChange={(event) => setLayer(event.target.value as Goal['layer'])}
        >
          {Object.entries(LAYER_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      {layer === 'GOAL' && (
        <label>
          Horyzont
          <select name="horizon" required defaultValue={goal?.horizon ?? undefined}>
            {Object.entries(HORIZON_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      )}
      <label>
        Kategoria
        <select name="category_code" defaultValue={goal?.category_code ?? ''}>
          <option value="">Bez kategorii</option>
          {domains.map((domain) => (
            <option key={domain.code} value={domain.code}>
              {domain.name_pl}
            </option>
          ))}
        </select>
      </label>
      <button type="submit" disabled={pending}>
        {submitLabel}
      </button>
    </form>
  )
}

function Section({
  title,
  layer,
  goals,
  domains,
  actions,
}: {
  title: string
  layer: Goal['layer']
  goals: Goal[]
  domains: Domain[]
  actions: ItemActions
}) {
  const mine = goals.filter((goal) => goal.layer === layer)
  const active = mine.filter((goal) => !goal.completed_at)
  const completed = mine.filter((goal) => goal.completed_at)

  return (
    <section>
      <h2>{title}</h2>
      <ul>
        {active.map((goal) => (
          <Item key={goal.id} goal={goal} domains={domains} actions={actions} />
        ))}
      </ul>
      {completed.length > 0 && (
        // Native disclosure rather than a state toggle: closed by default, keyboard-accessible and
        // no JavaScript of ours involved.
        <details>
          <summary>Ukończone ({completed.length})</summary>
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
  const done = Boolean(goal.completed_at)

  if (actions.editing === goal.id) {
    return (
      <li>
        <GoalForm
          name="Edytuj wpis"
          submitLabel="Zapisz"
          goal={goal}
          domains={domains}
          onSubmit={async (draft) => {
            // Editing must not silently un-complete an entry, so its own state rides along.
            const saved = await actions.replace(goal.id, draft, done)
            if (saved) actions.setEditing(null)
            return saved
          }}
        />
        <button type="button" onClick={() => actions.setEditing(null)}>
          Anuluj
        </button>
      </li>
    )
  }

  // The wire carries the code; the label lives in the shell data the outlet already handed us.
  const category = domains.find((domain) => domain.code === goal.category_code)?.name_pl
  const meta = [goal.horizon && HORIZON_LABEL[goal.horizon], category].filter(Boolean).join(' · ')

  return (
    <li>
      <p>{goal.content}</p>
      {meta && <small>{meta}</small>}
      <button type="button" onClick={() => actions.replace(goal.id, draftOf(goal), !done)}>
        {done ? 'Przywróć' : 'Ukończ'}
      </button>
      <button type="button" onClick={() => actions.setEditing(goal.id)}>
        Edytuj
      </button>
    </li>
  )
}
