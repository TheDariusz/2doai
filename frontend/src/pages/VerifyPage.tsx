import { useEffect, useEffectEvent, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { useAuth } from '../auth/auth-context'
import { awaitsCode } from '../auth/codeRequest'
import { readVerifyLink } from './verifyLink'
import { RESEND_COOLDOWN_SECONDS, type VerifyHandover, type VerifyMessageKey } from './verifyHandover'

/** `link` is a sign-in the mail link started on its own; it fails with its own message. */
type Action = 'signIn' | 'link' | 'resend'

/**
 * The code screen — where a session is opened. Reached three ways: from `/login` or `/register`
 * with a code on its way (route state), from the link in the mail (the fragment), or cold, after a
 * reload or from a bookmark (neither). The first code an account spends also proves its address.
 *
 * The address is an ordinary editable field rather than a line of text: this is the only route to
 * a fresh code, and a cold arrival has no state at all. Costing a returning user one retyped
 * address beats a screen that can do nothing for them.
 *
 * A mail link signs in on its own when this browser asked for that address's code (DEV-62,
 * `codeRequest.ts`); anywhere else it only prefills, and the button does the rest.
 */
export function VerifyPage() {
  const { t, i18n } = useTranslation()
  const { status, signInWithCode, resendCode } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const handover = location.state as VerifyHandover | null
  const [email, setEmail] = useState(handover?.email ?? '')
  const [code, setCode] = useState('')
  const [message, setMessage] = useState<VerifyMessageKey | null>(handover?.error ?? null)
  const [sent, setSent] = useState(false)
  const [pending, setPending] = useState(false)
  const [wait, lockResend] = useCountdown(handover?.resendAfter ?? 0)
  const destination = handover?.from ?? '/goals'

  // The mail link, whenever it arrives — on a fresh load, or as a hash change on a screen that is
  // already open (a phone reusing its tab, the installed app), which does not remount anything. So
  // the fragment is adopted when it changes, during render, rather than once on mount: read only
  // then, a second link was stripped by the effect below with its code still in it. The fragment
  // must not be read at all after that strip, which is why the fields, not the URL, hold its values.
  const [adoptedHash, setAdoptedHash] = useState('')
  // The link to sign in from without a press, when there is one — see the effect below.
  const [autoLink, setAutoLink] = useState<{ hash: string; email: string; code: string } | null>(null)
  if (location.hash !== adoptedHash) {
    setAdoptedHash(location.hash)
    if (location.hash) {
      const link = readVerifyLink(location.hash)
      if (link.email) {
        setEmail(link.email)
      }
      setCode(link.code ?? '')
      setMessage(null)
      setSent(false)
      setAutoLink(
        link.email && link.code && awaitsCode(link.email)
          ? { hash: location.hash, email: link.email, code: link.code }
          : null,
      )
    }
  }

  // The code does not belong in the address bar, the history entry or a screenshot of either. A
  // replace, so Back does not restore it; the state rides along so `from` survives the strip.
  useEffect(() => {
    if (location.hash) {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true, state: location.state })
    }
  }, [location, navigate])

  // The link came back to the browser that asked for its code, so there is nothing left to confirm.
  // Waits for the bootstrap to settle (a live session needs no code), and fires once per link: the
  // ref survives StrictMode's second effect run, which would otherwise spend the code twice and show
  // the second attempt's refusal.
  const attempted = useRef<string | null>(null)
  const signInFromLink = useEffectEvent((link: { email: string; code: string }) => {
    void run('link', () => signInWithCode(link.email, link.code), () => navigate(destination, { replace: true }))
      // Only this attempt says "signing in"; a later press of the button is an ordinary sign-in.
      .finally(() => setAutoLink(null))
  })
  useEffect(() => {
    if (autoLink && status === 'anonymous' && attempted.current !== autoLink.hash) {
      attempted.current = autoLink.hash
      signInFromLink(autoLink)
    }
  }, [autoLink, status])

  // Already signed in — a mail link opened in a tab that has a session, or the sign-in below having
  // just landed. Either way the same destination, so it does not matter which of the two navigates.
  if (status === 'authenticated') {
    return <Navigate to={destination} replace />
  }

  /** One shape for both actions: clear whatever the last one said, run it, report what this one says. */
  async function run(kind: Action, action: () => Promise<void>, onDone: () => void) {
    setMessage(null)
    setSent(false)
    setPending(true)
    try {
      await action()
      onDone()
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 429) {
        lockResend(failure.retryAfter ?? RESEND_COOLDOWN_SECONDS)
      }
      setMessage(messageFor(failure, kind))
    } finally {
      setPending(false)
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // `replace`, because a spent code is nothing to come back to with the browser's Back button.
    await run(
      'signIn',
      () => signInWithCode(email, code),
      () => navigate(destination, { replace: true }),
    )
  }

  function resend() {
    void run(
      'resend',
      () => resendCode(email),
      () => {
        setSent(true)
        lockResend(RESEND_COOLDOWN_SECONDS)
      },
    )
  }

  return (
    <main className="auth">
      <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
      <h1>{t('verify.heading')}</h1>
      {handover?.email && <p>{t('verify.hint', { email: handover.email })}</p>}
      <p>{t('verify.missing')}</p>
      <form onSubmit={submit}>
        <label>
          {t('auth.email')}
          <input
            name="email"
            type="email"
            required
            maxLength={320}
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          {t('verify.code')}
          {/* Native attributes rather than eight boxes wired together: the numeric keypad, the
              one-time-code autofill and the eight-digit rule are all the platform's, and the server
              checks the same pattern anyway. */}
          <input
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{8}"
            maxLength={8}
            required
            value={code}
            onChange={(event) => setCode(event.target.value)}
          />
        </label>
        {pending && autoLink && <p role="status">{t('verify.signingIn')}</p>}
        {message && <p role="alert">{t(message)}</p>}
        {sent && <p role="status">{t('verify.sent')}</p>}
        <button type="submit" disabled={pending}>
          {t('verify.submit')}
        </button>
        {/* Not a submit: a resend must work while the code field is empty, which is the whole
            state a user with no code is in. */}
        <button type="button" disabled={pending || wait > 0} onClick={resend}>
          {t('verify.sendAgain')}
        </button>
        {wait > 0 && <p>{t('verify.resendIn', { time: clock(wait) })}</p>}
      </form>
    </main>
  )
}

/**
 * Seconds left until `deadline`, ticking, plus a way to set a new one. Held as a deadline rather
 * than as a counter decremented once a second, because a backgrounded tab throttles its timers: a
 * counter would come back from a minute in another app still showing most of that minute.
 */
function useCountdown(initialSeconds: number): [number, (seconds: number) => void] {
  const [clockState, setClockState] = useState(() => {
    const now = Date.now()
    return { now, deadline: initialSeconds > 0 ? now + initialSeconds * 1000 : null }
  })
  const { now, deadline } = clockState

  useEffect(() => {
    if (deadline === null) {
      return
    }
    const timer = setInterval(() => {
      const tick = Date.now()
      setClockState({ now: tick, deadline: tick >= deadline ? null : deadline })
    }, 1000)
    return () => clearInterval(timer)
  }, [deadline])

  function lock(seconds: number) {
    const start = Date.now()
    setClockState({ now: start, deadline: start + seconds * 1000 })
  }

  return [deadline === null ? 0 : Math.max(0, Math.ceil((deadline - now) / 1000)), lock]
}

/** `m:ss` — the hourly cap's wait can be most of an hour, which a bare count of seconds hides. */
function clock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/**
 * Branches on the status, never on `detail`. A refused code is a 401 whatever the reason — wrong,
 * expired, spent or unknown, told apart nowhere on purpose — so one message covers all four. A 403
 * here is a stale CSRF token, which no copy about codes would describe.
 */
function messageFor(failure: unknown, kind: Action): VerifyMessageKey {
  if (!(failure instanceof ApiError)) {
    return 'auth.errors.generic'
  }
  if (failure.status === 401 || failure.status === 422) {
    if (kind === 'link') {
      return 'verify.errors.link'
    }
    return kind === 'signIn' ? 'verify.errors.code' : 'auth.errors.invalidEmail'
  }
  if (failure.status === 429) {
    return 'verify.errors.tooMany'
  }
  if (failure.status === 503) {
    return kind === 'resend' ? 'verify.errors.unavailable' : 'auth.errors.unavailable'
  }
  return 'auth.errors.generic'
}
