import { createContext, use } from 'react'
import type { Language } from '../i18n'

/**
 * The `User` schema from `openapi.yaml` — identity, plus the language this account reads in.
 *
 * `language` is required, as the spec makes it: `changeLanguage`'s "skip the PATCH when nothing
 * would change" guard compares against it, and an absent value would turn every pick into a write.
 * The two literals are copied from the spec and are the SPA's only copy of them — `AuthApiTest`
 * holds this file to `AppLanguage`, so this is the one line a rename has to go through.
 */
export type User = { id: string; email: string; language: 'PL' | 'EN' }

/**
 * The two spellings of one language: `AppLanguage` on the wire, the BCP 47 tag i18next and
 * `<html lang>` want. Tables rather than `toUpperCase()`/`toLowerCase()`, because the case change
 * is not the point — the point is that these are two closed sets that have to stay in step, and a
 * `Record` keyed on each says so to the compiler. A third language becomes two compile errors here
 * instead of a cast that quietly returns a string nothing speaks.
 */
export const ACCOUNT_LANGUAGE: Record<Language, User['language']> = { pl: 'PL', en: 'EN' }

export const APP_LANGUAGE: Record<User['language'], Language> = { PL: 'pl', EN: 'en' }

export type Auth = {
  user: User | null
  status: 'loading' | 'authenticated' | 'anonymous'
  login: (email: string, password: string) => Promise<void>
  /**
   * Registration does not open a session — the server returns 201, mails a six-digit code to the
   * address and the account stays inert until `verify` spends it.
   */
  register: (email: string, password: string) => Promise<void>
  /** Spends the mailed code. Opens no session either: the user signs in once the address is theirs. */
  verify: (email: string, code: string) => Promise<void>
  /**
   * Asks for a fresh code. Accepted for every address — whether one was actually sent is exactly
   * what the server declines to disclose — so a resolved promise is not evidence of an account.
   */
  resendCode: (email: string) => Promise<void>
  /**
   * FR-002 — moves the account's language, and the app with it. Here rather than in the menu that
   * offers it: `user.language` is the account's language, so whatever writes it has to be whatever
   * owns it, or the two go out of step the moment anything else reads the context.
   */
  changeLanguage: (language: Language) => Promise<void>
  logout: () => Promise<void>
  deleteAccount: (password: string) => Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

export function useAuth(): Auth {
  const auth = use(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be called inside <AuthProvider>')
  }
  return auth
}
