import { useState, type ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { AutoThemeIcon, MoonIcon, SunIcon } from './pages/icons'
import { THEMES, applyTheme, readTheme, type Theme } from './theme'

const ICON: Record<Theme, ComponentType> = { light: SunIcon, dark: MoonIcon, auto: AutoThemeIcon }

/**
 * The `LanguageSwitch` shape over the three themes. The header and the auth screens never mount
 * together, so local state seeded from storage is the whole subscription.
 */
export function ThemeSwitch() {
  const { t } = useTranslation()
  const [current, setCurrent] = useState(readTheme)

  return (
    <div className="seg" role="group" aria-label={t('layout.theme')}>
      {THEMES.map((theme) => {
        const Glyph = ICON[theme]
        return (
          <button
            key={theme}
            type="button"
            aria-label={t(`theme.${theme}`)}
            aria-pressed={theme === current}
            onClick={() => {
              applyTheme(theme)
              setCurrent(theme)
            }}
          >
            <Glyph />
          </button>
        )
      })}
    </div>
  )
}

/**
 * The header's form of the same choice: one button showing the theme in force, stepping through
 * `THEMES` on each press. The segmented group does not fit a phone-width header next to the
 * language switch, the account chip and log out.
 */
export function ThemeCycle() {
  const { t } = useTranslation()
  const [current, setCurrent] = useState(readTheme)
  const Glyph = ICON[current]

  return (
    <button
      type="button"
      className="icon-btn"
      aria-label={t('layout.themeCurrent', { theme: t(`theme.${current}`) })}
      onClick={() => {
        const next = THEMES[(THEMES.indexOf(current) + 1) % THEMES.length]
        applyTheme(next)
        setCurrent(next)
      }}
    >
      <Glyph />
    </button>
  )
}
