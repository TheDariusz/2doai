import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { AccountMenu, LogoutButton } from '../auth/AccountMenu'
import { useAuth } from '../auth/auth-context'
import type { Language } from '../i18n'
import { LanguageSwitch } from '../i18n/LanguageSwitch'

/**
  * A row of the `categories` resource — snake_case straight off the wire. `name` is already written
  * in the language the request negotiated, so the SPA renders it verbatim and never keys on it.
  */
export type Domain = { code: string; name: string }

/**
 * The colour a domain is drawn in, derived from its position rather than configured: the palette is
 * a fixed step around the oklch hue circle at one lightness and one chroma, so every domain is as
 * loud as the next and a twelfth one needs no design decision. Exported because the rail's dots and
 * the entries that carry a domain have to agree.
 */
export function domainColor(index: number): string {
  return `oklch(0.68 0.15 ${25 + index * 33})`
}

/**
 * The authenticated shell. The 11 life domains come from the server rather than a hard-coded list,
 * already ordered by `display_order` (`CategoryController` sorts them), so the rail renders them as
 * received. `CategorySyncCheck` guards the *code* list against `LifeDomain` drift; the labels and
 * ordering live in the Flyway seed alone and are guarded by nothing.
 */
export function AppLayout() {
  const { t, i18n } = useTranslation()
  const { changeLanguage } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [params] = useSearchParams()
  const [domains, setDomains] = useState<Domain[]>([])
  const [failed, setFailed] = useState(false)
  const [languageError, setLanguageError] = useState<string | null>(null)

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
          // must stop telling the user to refresh a page whose rail is already underneath the
          // notice.
          setFailed(false)
        }
      },
      // A 401 already routes to /login via the session-expired event. Anything else leaves a shell
      // the user cannot navigate, so say so instead of rendering an empty rail that looks finished.
      () => {
        if (current) {
          setFailed(true)
        }
      },
    )

    // Re-run on a language switch: the labels are server-rendered in the request's language, and
    // the switch changes it in place. This costs no database query — the controller answers from a
    // startup-built map — and a switch back is served from the browser cache, which
    // `Vary: Accept-Language` keys per language.
    return () => {
      current = false
    }
  }, [i18n.resolvedLanguage])

  /**
   * FR-002. The account owns the language and `AuthProvider.changeLanguage` owns the write — what
   * belongs here is what the user is told when it fails.
   */
  async function chooseLanguage(next: Language) {
    setLanguageError(null)
    try {
      await changeLanguage(next)
    } catch {
      // The switch reads the live i18next language, so a failed write leaves it where it was.
      setLanguageError(t('account.errors.language'))
    }
  }

  /**
   * The entries screen carrying the filters it already has, with one of them replaced. The rail is
   * one axis of three, so a category switch that rebuilt the query string from scratch would
   * silently widen the other two and leave the user looking at a list they never asked for.
   */
  function filteredUrl(key: 'category' | 'withdrawn', value: string | null) {
    const next = new URLSearchParams(params)
    if (value) {
      // Lowercased in the URL, uppercased on the wire: the query string is a link a user reads and
      // edits, the SCREAMING_CASE belongs to the enum behind it.
      next.set(key, value.toLowerCase())
    } else {
      next.delete(key)
    }
    const search = next.toString()
    return search ? `/goals?${search}` : '/goals'
  }

  const category = (params.get('category') ?? '').toUpperCase()

  return (
    <div className="shell">
      <header className="topbar">
        <div className="wordmark">
          {/* The mark from `favicon.svg`, at the one size the header needs it. */}
          <svg width="22" height="22" viewBox="0 0 48 46" fill="none" aria-hidden="true">
            <path fill="#863bff" d="M25.946 44.938c-.664.845-2.021.375-2.021-.698V33.937a2.26 2.26 0 0 0-2.262-2.262H10.287c-.92 0-1.456-1.04-.92-1.788l7.48-10.471c1.07-1.497 0-3.578-1.842-3.578H1.237c-.92 0-1.456-1.04-.92-1.788L10.013.474c.214-.297.556-.474.92-.474h28.894c.92 0 1.456 1.04.92 1.788l-7.48 10.471c-1.07 1.498 0 3.579 1.842 3.579h11.377c.943 0 1.473 1.088.89 1.83L25.947 44.94z" />
          </svg>
          <span>2do AI</span>
        </div>

        <div className="topbar-controls">
          <LanguageSwitch onSelect={chooseLanguage} />
          {languageError && <p role="alert">{languageError}</p>}
          <AccountMenu />
          <LogoutButton />
        </div>
      </header>

      <div className="app-body">
        <aside className="rail">
          <p className="eyebrow">{t('layout.domains')}</p>
          {failed && <p className="rail-notice">{t('layout.domainsFailed')}</p>}

          <nav aria-label={t('layout.nav')}>
            {/* `aria-current` is both the state and the styling hook — one source of truth for
                which tag is on, and the one a screen reader already reads. */}
            <Link
              className="tag"
              to={filteredUrl('category', null)}
              // Unfiltered means the entries screen with no category on it — not merely the absence
              // of the parameter, which any other screen also has.
              aria-current={pathname === '/goals' && !category ? 'page' : undefined}
            >
              <span className="dot" style={{ background: 'var(--accent)' }} />
              {t('goals.filters.all')}
            </Link>
            {domains.map((domain, index) => (
              <Link
                key={domain.code}
                className="tag"
                to={filteredUrl('category', domain.code)}
                aria-current={domain.code === category ? 'page' : undefined}
              >
                <span className="dot" style={{ background: domainColor(index) }} />
                {domain.name}
              </Link>
            ))}
          </nav>

          <hr />

          {/* The third axis, and the only one that is on/off. It writes `1` because the value is
              never read — the parameter's presence is the whole signal. */}
          <label className="rail-switch">
            {t('goals.filters.withdrawn')}
            <input
              type="checkbox"
              checked={params.has('withdrawn')}
              onChange={(event) => navigate(filteredUrl('withdrawn', event.target.checked ? '1' : null))}
            />
          </label>
        </aside>

        <main>
          <Outlet context={domains} />
        </main>
      </div>
    </div>
  )
}
