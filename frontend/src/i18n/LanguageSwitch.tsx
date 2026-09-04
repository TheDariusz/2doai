import { useTranslation } from 'react-i18next'
import type { Language } from './index'

/**
 * The control behind both switches (FR-001 on the auth screens, FR-002 in the account menu). What
 * happens on a pick is the caller's business — before login the choice is local, after it the
 * account owns it — so this holds the markup and the labels and nothing else.
 */
export function LanguageSwitch({ onSelect }: { onSelect: (language: Language) => void }) {
  const { t, i18n } = useTranslation()

  return (
    <label>
      {t('layout.language')}
      <select
        value={i18n.resolvedLanguage ?? 'en'}
        onChange={(event) => onSelect(event.target.value as Language)}
      >
        {/* Endonyms, never translated: someone who cannot read the current language still has to
            be able to find their own in the list. */}
        <option value="pl">Polski</option>
        <option value="en">English</option>
      </select>
    </label>
  )
}
