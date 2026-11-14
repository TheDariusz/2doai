/**
 * Whether this browser asked for a sign-in code — and for which address — in the last 15 minutes
 * (DEV-62). It decides whether the mail link may sign in on its own.
 *
 * The link carries a working credential, so following it is not proof that the person reading the
 * mail is the one who asked. A mail scanner's browser follows every link (and runs JavaScript), and
 * a link somebody else sent would sign this browser into *their* account (login CSRF). Neither of
 * those asked for a code from this browser. The user who requested a code a minute ago and clicked
 * the link in the same browser did — so for them, and only them, the screen signs in without a press.
 * Everyone else gets the prefilled form and one button.
 *
 * This decides whether a press is skipped, nothing more — it is not a security boundary. The server
 * still demands the right single-use code, fresh from the owner's mailbox, from any browser, and a
 * script able to write this entry already runs on the app's origin and owns the session anyway.
 * Binding the code to a server-issued nonce would add a schema and API change for no protection
 * beyond what that already concedes.
 *
 * `localStorage`, not `sessionStorage`: a mail client opens the link in a new tab, and session
 * storage is per tab. Every access is guarded — storage can throw (private mode, blocked site data),
 * and the fallback is the prefilled form, which is always correct.
 */

export const CODE_REQUEST_KEY = 'todoai.codeRequest'

/** `EmailVerificationService.CODE_VALIDITY` — past it, the code the request produced is dead anyway. */
const CODE_VALIDITY_MS = 15 * 60_000

type CodeRequest = { email: string; at: number }

/** The server's normalisation (`user/Email`): trimmed and lower-cased. */
function normalize(email: string): string {
  return email.trim().toLowerCase()
}

export function rememberCodeRequest(email: string, now: number = Date.now()): void {
  try {
    localStorage.setItem(CODE_REQUEST_KEY, JSON.stringify({ email: normalize(email), at: now } satisfies CodeRequest))
  } catch {
    // No storage, no auto sign-in: the link falls back to the form.
  }
}

export function awaitsCode(email: string, now: number = Date.now()): boolean {
  try {
    const stored = localStorage.getItem(CODE_REQUEST_KEY)
    if (!stored) {
      return false
    }
    const request = JSON.parse(stored) as Partial<CodeRequest>
    return (
      typeof request.email === 'string' &&
      typeof request.at === 'number' &&
      request.email === normalize(email) &&
      now - request.at < CODE_VALIDITY_MS
    )
  } catch {
    return false
  }
}

export function forgetCodeRequest(): void {
  try {
    localStorage.removeItem(CODE_REQUEST_KEY)
  } catch {
    // Nothing to forget where nothing could be stored.
  }
}
