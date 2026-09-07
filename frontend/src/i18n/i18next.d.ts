import type { pl } from './pl'

/**
 * Types `t()` against the actual catalog: a misspelled or removed key is a build failure, not a
 * key name rendered on screen. `en.ts` is typed as `typeof pl` for the other half of the guard —
 * between them, the two catalogs cannot drift apart.
 */
declare module 'i18next' {
  interface CustomTypeOptions {
    resources: { translation: typeof pl }
  }
}
