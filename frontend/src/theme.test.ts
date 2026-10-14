import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { applyTheme, readTheme } from './theme'

const KEY = '2doai.theme'

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
})
