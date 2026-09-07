import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

// A non-ASCII character in a string is copy in disguise: no identifier, path or wire literal in this
// app needs one, and every screen string that did now lives in src/i18n. It is a diacritic detector,
// not a copy detector — a Polish literal without diacritics or a hardcoded English one passes — so
// it catches the leak that is easy to see and relies on review for the rest. The one exception is
// the middot the entry meta joins on, which is punctuation rather than words — allowed below.
const NON_ASCII = String.raw`[^\x00-\x7F·]`

const noStrayCopy = [
  { selector: `Literal[value=/${NON_ASCII}/]`, kind: 'A string literal' },
  { selector: `TemplateElement[value.raw=/${NON_ASCII}/]`, kind: 'A template literal' },
  { selector: `JSXText[value=/${NON_ASCII}/]`, kind: 'Text in JSX' },
].map(({ selector, kind }) => ({
  selector,
  message: `${kind} outside src/i18n is hardcoded copy — put it in the catalog and render it with t().`,
}))

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      'no-restricted-syntax': ['error', ...noStrayCopy],
      // The domain palette is derived from the rail's own ordering, so it lives beside the rail and
      // is imported from there by whatever draws a domain. Naming it here costs the shell module
      // its fast-refresh boundary in dev and nothing else.
      'react-refresh/only-export-components': ['error', { allowExportNames: ['domainColor'] }],
    },
  },
  {
    // The catalogs are where the copy belongs; the tests carry user-typed *content* (an entry
    // someone wrote in Polish stays Polish however the app is rendered) and the odd Polish probe.
    files: ['src/i18n/pl.ts', 'src/i18n/en.ts', 'src/test/**', '**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-syntax': 'off',
    },
  },
])
