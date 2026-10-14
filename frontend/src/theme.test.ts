import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import indexHtml from '../index.html?raw'
import { CHROME, STORED, applyTheme, readTheme } from './theme'

const KEY = '2doai.theme'

// The first-paint copy of the rule, inlined in index.html before any module loads.
const inlineScript = new Function(indexHtml.match(/<script>([\s\S]*?)<\/script>/)![1])

function osPrefersDark(dark: boolean) {
  vi.stubGlobal('matchMedia', () => ({ matches: dark, addEventListener: vi.fn() }))
}

describe('theme', () => {
  beforeEach(() => {
    document.head.innerHTML = '<meta name="theme-color" content="#ffffff" />'
    delete document.documentElement.dataset.theme
    osPrefersDark(false)
  })
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('falls back to auto for nothing stored and for garbage', () => {
    expect(readTheme()).toBe('auto')
    localStorage.setItem(KEY, 'sepia')
    expect(readTheme()).toBe('auto')
  })

  it('reads a stored light or dark', () => {
    localStorage.setItem(KEY, 'dark')
    expect(readTheme()).toBe('dark')
    localStorage.setItem(KEY, 'light')
    expect(readTheme()).toBe('light')
  })

  it('yields auto, without throwing, when storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })
    expect(readTheme()).toBe('auto')
  })

  it('a concrete choice wins over the OS', () => {
    osPrefersDark(true)
    applyTheme('light')
    expect(document.documentElement.dataset.theme).toBe('light')
    osPrefersDark(false)
    applyTheme('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('auto follows the OS', () => {
    osPrefersDark(true)
    applyTheme('auto')
    expect(document.documentElement.dataset.theme).toBe('dark')
    osPrefersDark(false)
    applyTheme('auto')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('persists the choice and moves the theme-color meta', () => {
    applyTheme('dark')
    expect(localStorage.getItem(KEY)).toBe('dark')
    expect(document.querySelector('meta[name="theme-color"]')).toHaveAttribute('content', '#1e1f27')
    applyTheme('light')
    expect(document.querySelector('meta[name="theme-color"]')).toHaveAttribute('content', '#ffffff')
  })

  it('still applies when the write throws', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })
    expect(() => applyTheme('dark')).not.toThrow()
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('repaints when another tab changes the stored choice', () => {
    localStorage.setItem(KEY, 'dark')
    window.dispatchEvent(new StorageEvent('storage', { key: KEY }))
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('index.html paints first with the same key, rule and status-bar colours', () => {
    expect(STORED).toBe(KEY)
    expect(indexHtml).toContain(`<meta name="theme-color" content="${CHROME.light}" />`)

    localStorage.setItem(KEY, 'dark')
    inlineScript()
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.querySelector('meta[name="theme-color"]')).toHaveAttribute('content', CHROME.dark)

    document.head.innerHTML = `<meta name="theme-color" content="${CHROME.light}" />`
    localStorage.setItem(KEY, 'auto')
    osPrefersDark(false)
    inlineScript()
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.querySelector('meta[name="theme-color"]')).toHaveAttribute('content', CHROME.light)
  })
})
