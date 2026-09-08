import { ApiError } from '../api/client'

/**
 * The Problem `type` URNs the backend puts on the 403s the SPA has to tell apart. `openapi.yaml` is
 * the anchor for these literals, not this file: `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`
 * and `VerificationApiTest.emitsTheVerificationUrnsTheContractAndTheSpaBothHardcode` hold the spec,
 * these lines and the server's values together, so a rename on any one side goes red (lessons.md).
 *
 * All of them live here rather than in the screen that reads each: they are one kind of constant,
 * and two homes for it means the next one goes wherever its author last looked — and the guard above
 * is then reading a file the value has moved out of.
 */
export const RE_AUTH_FAILED = 'urn:2doai:problem:re-auth-failed'
export const EMAIL_NOT_VERIFIED = 'urn:2doai:problem:email-not-verified'
export const VERIFICATION_FAILED = 'urn:2doai:problem:verification-failed'

/** The URNs above, as a type — so nothing else can be asked for. */
export type ProblemType = typeof RE_AUTH_FAILED | typeof EMAIL_NOT_VERIFIED | typeof VERIFICATION_FAILED

/**
 * Does this failure carry that URN? The only way to ask, because `ApiError.type` stays a plain
 * `string` — the server may send a URN this build has never heard of — so a comparison written
 * inline would typecheck against any misspelling. Going through here, it cannot.
 */
export function isProblem(failure: unknown, type: ProblemType): boolean {
  return failure instanceof ApiError && failure.type === type
}
