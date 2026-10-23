import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { Wordmark } from '../layout/Wordmark'
import { CHROME } from '../theme'
import { GitHubIcon, LinkedInIcon } from './icons'

const AUTHOR_SITE = 'https://thedariusz.dev'
const EXTERNAL = { target: '_blank', rel: 'noopener noreferrer' } as const

export function LandingPage() {
  const { status } = useAuth()
  const { t, i18n } = useTranslation()

  // The landing is pinned light whatever the theme (index.css), so the status bar is too while it shows.
  useEffect(() => {
    const meta = document.querySelector('meta[name="theme-color"]')
    const previous = meta?.getAttribute('content')
    meta?.setAttribute('content', CHROME.light)
    return () => {
      if (previous) meta?.setAttribute('content', previous)
    }
  }, [])

  if (status === 'authenticated') return <Navigate to="/goals" replace />

  return (
    <div className="landing">
      <header className="landing-header">
        <Wordmark />
        <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
        <Link className="text-link" to="/login">{t('auth.login.heading')}</Link>
      </header>
      <main>
        <section className="landing-hero" aria-labelledby="landing-title">
          <div className="landing-pitch">
            <h1 id="landing-title">{t('landing.title')}</h1>
            <p>{t('landing.intro')}</p>
            <div className="landing-actions">
              <Link className="cta" to="/register">{t('auth.register.heading')}</Link>
              <a className="text-link" href="#how-it-works">{t('landing.seeHow')}</a>
            </div>
          </div>
          <figure className="landing-preview" aria-labelledby="landing-example">
            <figcaption id="landing-example">{t('landing.example')}</figcaption>
            <h2>{t('goals.title')}</h2>
            {(['TASK', 'GOAL', 'DREAM'] as const).map((layer) => (
              <section key={layer} className={layer === 'DREAM' ? 'preview-row dream' : 'preview-row'}>
                <h3>{t(`goals.sections.${layer}`)}</h3>
                <p>{t(`landing.preview.${layer}`)}</p>
              </section>
            ))}
            <section className="preview-suggestion">
              <h3>{t('landing.preview.suggestion')}</h3>
              <p>{t('landing.preview.message')}</p>
              <p>{t('landing.preview.choices')}</p>
            </section>
          </figure>
        </section>
        <section id="how-it-works" aria-labelledby="landing-how">
          <h2 id="landing-how">{t('landing.howTitle')}</h2>
          <ol className="landing-steps">
            {(['capture', 'revisit', 'start'] as const).map((step) => (
              <li key={step}>
                <h3>{t(`landing.how.${step}.title`)}</h3>
                <p>{t(`landing.how.${step}.body`)}</p>
              </li>
            ))}
          </ol>
        </section>
        <section className="landing-faq" aria-labelledby="landing-faq">
          <h2 id="landing-faq">{t('landing.faqTitle')}</h2>
          {(['account', 'suggestions', 'control', 'deletion'] as const).map((topic) => (
            <details key={topic}>
              <summary>{t(`landing.faq.${topic}.question`)}</summary>
              <p>{t(`landing.faq.${topic}.answer`)}</p>
            </details>
          ))}
        </section>
        <section className="landing-closing" aria-labelledby="landing-start">
          <h2 id="landing-start">{t('landing.closing')}</h2>
          <Link className="cta" to="/register">{t('auth.register.heading')}</Link>
        </section>
      </main>
      <footer className="landing-footer">
        <div className="landing-author">
          {/* The author's mark, redrawn for a light page; the dark original would sit as a black square here. */}
          <svg className="author-mark" viewBox="0 0 32 32" aria-hidden="true">
            <rect width="32" height="32" rx="6" fill="#fff" stroke="#ebe6e1" />
            <circle cx="5.5" cy="21" r="2" fill="#e11d48" />
            <text x="10" y="23" fontFamily="Inter, Arial, sans-serif" fontWeight="700" fontSize="15" fill="#25221e">TD</text>
          </svg>
          <p>
            <span>{t('landing.footer.credit')}</span>
            <strong><a href={AUTHOR_SITE} {...EXTERNAL}>TheDariusz</a> · {t('landing.footer.name')}</strong>
            <span>{t('landing.footer.role')}</span>
          </p>
        </div>
        <nav className="landing-social" aria-label={t('landing.footer.links')}>
          <a href={AUTHOR_SITE} {...EXTERNAL}>thedariusz.dev</a>
          <a href="https://github.com/thedariusz" aria-label="GitHub" {...EXTERNAL}><GitHubIcon /></a>
          <a href="https://www.linkedin.com/in/dariusz-szczepanski-dev" aria-label="LinkedIn" {...EXTERNAL}><LinkedInIcon /></a>
        </nav>
      </footer>
    </div>
  )
}
