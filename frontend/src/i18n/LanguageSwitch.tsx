import { useTranslation } from 'react-i18next'
import { LANGUAGES, type Language } from './index'

/**
 * The endonym of each language, never translated: someone who cannot read the language the screen
 * is in still has to be able to find their own. The tag is what fits a 36px control, so the endonym
 * is the accessible name behind it rather than the visible text.
 */
const ENDONYM: Record<Language, string> = { pl: 'Polski', en: 'English' }

/**
 * The control behind both switches (FR-001 on the auth screens, FR-002 in the header). What happens
 * on a pick is the caller's business — before login the choice is local, after it the account owns
 * it — so this holds the markup and the labels and nothing else.
 *
 * A segmented group rather than a `<select>`: with two options the list is the control, and the
 * pressed button says which language is on without opening anything.
 */
export function LanguageSwitch({ onSelect }: { onSelect: (language: Language) => void }) {
  const { t, i18n } = useTranslation()
  const current = i18n.resolvedLanguage ?? 'en'

  return (
    <div className="seg" role="group" aria-label={t('layout.language')}>
      {LANGUAGES.map((language) => (
        <button
          key={language}
          type="button"
          aria-label={ENDONYM[language]}
          aria-pressed={language === current}
          onClick={() => onSelect(language)}
        >
          {language.toUpperCase()}
        </button>
      ))}
    </div>
  )
}
