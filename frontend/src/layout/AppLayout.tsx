import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { api } from '../api/client'
import { AccountMenu } from '../auth/AccountMenu'

/** A row of the `categories` resource — snake_case straight off the wire. */
export type Domain = { code: string; name_pl: string; display_order: number }

/**
 * The authenticated shell. The 11 life domains come from the server rather than a hard-coded list.
 * `CategorySyncCheck` guards the *code* list against `LifeDomain` drift; the labels and ordering
 * rendered here live in the Flyway seed alone and are guarded by nothing.
 */
export function AppLayout() {
  const [domains, setDomains] = useState<Domain[]>([])
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api<{ items: Domain[] }>('/categories').then(
      (page) => setDomains([...page.items].sort((a, b) => a.display_order - b.display_order)),
      // A 401 already routes to /login via the session-expired event. Anything else leaves a shell
      // the user cannot navigate, so say so instead of rendering an empty nav that looks finished.
      () => setFailed(true),
    )
  }, [])

  return (
    <div className="shell">
      <header>
        <strong>2do AI</strong>
        <AccountMenu />
      </header>

      <nav aria-label="Domeny życia">
        <ul>
          {failed && <li>Nie udało się wczytać domen — odśwież stronę.</li>}
          {domains.map((domain) => (
            <li key={domain.code}>
              <NavLink to={`/domena/${domain.code}`}>{domain.name_pl}</NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <main>
        <Outlet context={domains} />
      </main>
    </div>
  )
}
