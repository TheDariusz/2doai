import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeCycle, ThemeSwitch } from './ThemeSwitch'

describe('ThemeSwitch', () => {
  it('starts on auto, and a pick applies, persists and moves the pressed state', async () => {
    const user = userEvent.setup()
    render(<ThemeSwitch />)

    expect(screen.getByRole('group', { name: 'Theme' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Auto' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Dark' }))

    expect(screen.getByRole('button', { name: 'Dark' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Auto' })).toHaveAttribute('aria-pressed', 'false')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('2doai.theme')).toBe('dark')
  })

  it('follows a pick made in another tab', () => {
    render(<ThemeSwitch />)

    localStorage.setItem('2doai.theme', 'dark')
    act(() => {
      window.dispatchEvent(new StorageEvent('storage', { key: '2doai.theme' }))
    })

    expect(screen.getByRole('button', { name: 'Dark' })).toHaveAttribute('aria-pressed', 'true')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})

describe('ThemeCycle', () => {
  it('names the theme in force and steps light, dark, auto on each press', async () => {
    const user = userEvent.setup()
    render(<ThemeCycle />)

    await user.click(screen.getByRole('button', { name: 'Theme: Auto' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('2doai.theme')).toBe('light')

    await user.click(screen.getByRole('button', { name: 'Theme: Light' }))
    expect(document.documentElement.dataset.theme).toBe('dark')

    await user.click(screen.getByRole('button', { name: 'Theme: Dark' }))
    expect(localStorage.getItem('2doai.theme')).toBe('auto')
    expect(screen.getByRole('button', { name: 'Theme: Auto' })).toBeInTheDocument()
  })
})
