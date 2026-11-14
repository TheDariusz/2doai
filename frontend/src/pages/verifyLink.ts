/**
 * Reads the link the code mail carries: `/verify#email=<address>&code=<code>`.
 *
 * The address and code ride in the fragment, which the browser never sends to a server, so neither
 * reaches a log on the way in. The link only prefills the code screen; nothing is spent until the
 * user presses the button, so a mail scanner that follows it signs nobody in.
 *
 * The parameter names are a contract with the backend's `SignInLink`, which writes them;
 * `VerificationApiTest.buildsTheMailLinkTheSpaRoutesAndParses` reads both files, so renaming one side
 * goes red. The route path is held the same way against `App.tsx`.
 */

/** Exactly the code's shape — the backend's `EmailVerificationService.CODE_LENGTH`. */
const CODE = /^\d{8}$/

export type VerifyLink = { email: string | null; code: string | null }

/**
 * @param hash `location.hash`, with or without its leading `#`
 * @returns whatever the fragment carries; a code of the wrong shape is dropped rather than
 *   prefilled, since typing it would only spend a guess
 */
export function readVerifyLink(hash: string): VerifyLink {
  const params = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash)
  const email = params.get('email')
  const code = params.get('code')
  return { email: email || null, code: code !== null && CODE.test(code) ? code : null }
}
