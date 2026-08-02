/**
 * The one place that talks to the backend. Same-origin by design (Pattern B): Vite proxies `/api`
 * in dev, Cloudflare does it in production, so there is no base URL and no CORS on either side.
 */

const CSRF_COOKIE = 'XSRF-TOKEN='

/** Methods Spring Security exempts from CSRF — everything else must carry the token. */
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

/** A backend failure carrying its HTTP status, so callers can map 401 / 403 / 409 to real copy. */
export class ApiError extends Error {
  status: number

  constructor(status: number, detail: string) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
  }
}

function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(CSRF_COOKIE))
    ?.slice(CSRF_COOKIE.length)
}

export async function api<T = void>(
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<T> {
  const method = init.method ?? 'GET'
  const headers: Record<string, string> = {}

  if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (!SAFE_METHODS.has(method)) {
    // Double-submit: the cookie is readable by JS on purpose, the header is what a cross-site
    // form cannot forge. `CsrfCookieFilter` primes the cookie on the very first request.
    const token = csrfToken()
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    credentials: 'include',
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })

  if (!response.ok) {
    // RFC 9457 Problem JSON; fall back to the status line if the body is not one.
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? response.statusText)
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}
