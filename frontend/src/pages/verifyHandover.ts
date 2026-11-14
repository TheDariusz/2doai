import type { Path } from 'react-router'

/**
 * The catalog keys the code screen can put in front of the user, its own and those it is handed.
 * Held as keys rather than as resolved sentences because the screen carries the language switch: a
 * message stored as text would keep saying whatever it said when it was raised, in a language the
 * user has since left.
 */
export type VerifyMessageKey =
  | 'verify.errors.code'
  | 'verify.errors.link'
  | 'verify.errors.tooMany'
  | 'verify.errors.unavailable'
  | 'auth.errors.invalidEmail'
  | 'auth.errors.unavailable'
  | 'auth.errors.generic'

/**
 * What the screens that request a code hand to `/verify` in route state: the address, to save
 * retyping it; where a bounced visitor was going, so the sign-in can finish there; a reason, where
 * there was one; and how many seconds the resend stays locked.
 */
export type VerifyHandover = {
  email?: string
  from?: Partial<Path>
  error?: VerifyMessageKey
  resendAfter?: number
}

/**
 * How long the resend stays locked after a code went out. The number is the server's
 * `VerificationThrottle` cooldown, copied rather than read: nothing on the wire carries it before a
 * 429 does. Drift is self-correcting rather than silent — unlocked early, the next press gets a 429
 * whose `Retry-After` restarts the countdown with the server's own figure.
 */
export const RESEND_COOLDOWN_SECONDS = 60
