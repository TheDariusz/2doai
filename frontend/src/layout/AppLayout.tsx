import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { api } from '../api/client'
import { AccountMenu } from '../auth/AccountMenu'

/**
  * A row of the `categories` resource — snake_case straight off the wire. `name` is already written
  * in the language the request negotiated, so the SPA renders it verbatim and never keys on it.
  */
export type Domain = { code: string; name: string }

/**
 * The authenticated shell. The 11 life domains come from the server rather than a hard-coded list,
 * already ordered by `display_order` (`CategoryController` sorts them), so the nav renders them as
 * received. `CategorySyncCheck` guards the *code* list against `LifeDomain` drift; the labels and
 * ordering live in the Flyway seed alone and are guarded by nothing.
 */
export function AppLayout() {
  const { t, i18n } = useTranslation()
  const [domains, setDomains] = useState<Domain[]>([])
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    // Only the load this effect started may speak. A cold Fly machine can leave the first request
    // outstanding long enough for an impatient switch to overtake it, and the answer that lands
    // last would otherwise win — labels in the language the user just left.
    let current = true

    api<{ items: Domain[] }>('/categories').then(
      (page) => {
        if (current) {
          setDomains(page.items)
          // Cleared, not just set: the load runs once per language now, so a shell that recovered
          // must stop telling the user to refresh a page whose nav is already underneath the notice.
          setFailed(false)
        }
      },
      // A 401 already routes to /login via the session-expired event. Anything else leaves a shell
      // the user cannot navigate, so say so instead of rendering an empty nav that looks finished.
      () => {
        if (current) {
          setFailed(true)
        }
      },
    )

    // Re-run on a language switch: the labels are server-rendered in the request's language, and
    // the account-menu switch changes it in place. This costs no database query — the controller
    // answers from a startup-built map — and a switch back is served from the browser cache, which
    // `Vary: Accept-Language` keys per language.
    return () => {
      current = false
    }
  }, [i18n.resolvedLanguage])

  return (
    <div className="shell">
      <header>
        <strong>2do AI</strong>
        <AccountMenu />
      </header>

      <nav aria-label={t('layout.nav')}>
        <ul>
          <li>
            <NavLink to="/goals">{t('goals.title')}</NavLink>
          </li>
          {failed && <li>{t('layout.domainsFailed')}</li>}
          {domains.map((domain) => (
            <li key={domain.code}>
              <NavLink to={`/domain/${domain.code.toLowerCase()}`}>{domain.name}</NavLink>
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
