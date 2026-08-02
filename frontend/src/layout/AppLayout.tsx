import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { api } from '../api/client'
import { AccountMenu } from '../auth/AccountMenu'

/** A row of the `categories` resource — snake_case straight off the wire. */
export type Domain = { code: string; name_pl: string; display_order: number }

/**
 * The authenticated shell. The 11 life domains come from the server rather than a hard-coded
 * list, so `LifeDomain` stays the single source of truth (`CategorySyncCheck` guards the seed).
 */
export function AppLayout() {
  const [domains, setDomains] = useState<Domain[]>([])

  useEffect(() => {
    api<{ items: Domain[] }>('/categories').then(
      (page) => setDomains([...page.items].sort((a, b) => a.display_order - b.display_order)),
      () => setDomains([]),
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
