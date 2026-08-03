/**
 * The one place that talks to the backend. Same-origin by design (Pattern B): Vite proxies `/api`
 * in dev, Cloudflare does it in production, so there is no base URL and no CORS on either side.
 */

const CSRF_COOKIE = 'XSRF-TOKEN='

type Method = 'GET' | 'HEAD' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** A backend failure carrying its HTTP status, so callers can map it to copy that fits. */
export class ApiError extends Error {
  readonly status: number

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
  init: { method?: Method; body?: unknown } = {},
): Promise<T> {
  const method = init.method ?? 'GET'
  const headers: Record<string, string> = {}

  if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  // GET and HEAD go without a token, matching Spring Security's CSRF exemptions.
  if (method !== 'GET' && method !== 'HEAD') {
    // Double-submit: the cookie is readable by JS on purpose, the header is what a cross-site
    // form cannot forge. The server primes it on every response, including the anonymous
    // bootstrap 401 — so no cookie means that response has not landed yet.
    const token = csrfToken()
    if (!token) {
      // Fail here rather than send a request the server is bound to reject: its 403 is
      // indistinguishable from the 403 a wrong re-auth password produces. Status 0 marks a
      // failure that never reached the server; screens map it to their generic copy.
      throw new ApiError(0, 'No XSRF-TOKEN cookie — the priming response has not landed yet')
    }
    headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    credentials: 'include',
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })

  if (!response.ok) {
    if (response.status === 401) {
      // The session ended server-side (timeout, or deleted from another device). Announced once
      // here rather than handled in each caller — `AuthProvider` listens and drops to anonymous,
      // which is what sends the user back to /login from wherever they were.
      window.dispatchEvent(new Event('session-expired'))
    }
    // RFC 9457 Problem JSON. The fallback is the status, not `statusText`: the latter is always
    // empty over HTTP/2, which is what both Cloudflare and Fly serve.
    const problem = await response.json().catch(() => null)
    throw new ApiError(response.status, problem?.detail ?? `HTTP ${response.status}`)
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}
