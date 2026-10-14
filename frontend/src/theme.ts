/** What the user can pick. `auto` means "follow the OS" and is the default for a device that never chose. */
export const THEMES = ['light', 'dark', 'auto'] as const

export type Theme = (typeof THEMES)[number]

/** Device-level, like the pre-login language: no account column. */
export const STORED = '2doai.theme'

const DARK_OS = '(prefers-color-scheme: dark)'

/** Status-bar colours; the page background in each scheme (`--bg` in index.css). */
export const CHROME = { light: '#ffffff', dark: '#1e1f27' } as const

/**
 * Storage is read inside a `try`: a browser set to block site data throws on every `localStorage`
 * member rather than returning null (see `i18n/index.ts`). Losing the choice just means auto.
 */
export function readTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORED)
    return THEMES.find((theme) => theme === stored) ?? 'auto'
  } catch {
    return 'auto'
  }
}

// `?.` because jsdom has no matchMedia; an absent one reads as a light OS.
const osIsDark = () => window.matchMedia?.(DARK_OS).matches ?? false

/**
 * Writes the resolved scheme as `data-theme` on `<html>`, which is all the CSS reads. `index.html`
 * runs the same three-line rule inline before first paint; this owns every change after that.
 */
function paintTheme(theme: Theme): void {
  const resolved = theme === 'auto' ? (osIsDark() ? 'dark' : 'light') : theme
  document.documentElement.dataset.theme = resolved
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', CHROME[resolved])
}

/** A user's pick: persisted, then painted. Boot and OS changes only paint, so they never overwrite it. */
export function applyTheme(theme: Theme): void {
  try {
    localStorage.setItem(STORED, theme)
  } catch {
    // Storage unavailable: the pick still applies to this tab; only the next boot forgets it.
  }
  paintTheme(theme)
}

/** True for a `storage` event that may have changed the theme (`key` is null when storage was cleared). */
export const isThemeChange = (event: StorageEvent) => event.key === STORED || event.key === null

// Side effect, like `./i18n`: first render already sees the right scheme, auto tracks the OS live,
// and a pick made in another tab repaints this one.
paintTheme(readTheme())
window.matchMedia?.(DARK_OS).addEventListener('change', () => {
  if (readTheme() === 'auto') {
    paintTheme('auto')
  }
})
window.addEventListener('storage', (event) => {
  if (isThemeChange(event)) {
    paintTheme(readTheme())
  }
})
