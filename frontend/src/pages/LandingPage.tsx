import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { LanguageSwitch } from '../i18n/LanguageSwitch'

export function LandingPage() {
  const { status } = useAuth()
  const { t, i18n } = useTranslation()

  if (status === 'authenticated') return <Navigate to="/goals" replace />

  return (
    <div className="landing">
      <header>
        <span className="wordmark">2do AI</span>
        <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
        <Link to="/login">{t('auth.login.heading')}</Link>
      </header>
      <main>
        <section aria-labelledby="landing-title">
          <div>
            <h1 id="landing-title">{t('landing.title')}</h1>
            <p>{t('landing.intro')}</p>
            <Link to="/register">{t('auth.register.heading')}</Link>
            <a href="#how-it-works">{t('landing.seeHow')}</a>
          </div>
          <figure aria-labelledby="landing-example">
            <figcaption id="landing-example">{t('landing.example')}</figcaption>
            <h2>{t('goals.title')}</h2>
            {(['TASK', 'GOAL', 'DREAM'] as const).map((layer) => (
              <section key={layer}>
                <h3>{t(`goals.sections.${layer}`)}</h3>
                <p>{t(`landing.preview.${layer}`)}</p>
              </section>
            ))}
            <section>
              <h3>{t('landing.preview.suggestion')}</h3>
              <p>{t('landing.preview.message')}</p>
              <p>{t('landing.preview.choices')}</p>
            </section>
          </figure>
        </section>
        <section id="how-it-works" aria-labelledby="landing-how">
          <h2 id="landing-how">{t('landing.howTitle')}</h2>
          <ol>
            {(['capture', 'revisit', 'start'] as const).map((step) => (
              <li key={step}>
                <h3>{t(`landing.how.${step}.title`)}</h3>
                <p>{t(`landing.how.${step}.body`)}</p>
              </li>
            ))}
          </ol>
        </section>
        <section aria-labelledby="landing-faq">
          <h2 id="landing-faq">{t('landing.faqTitle')}</h2>
          {(['account', 'suggestions', 'control', 'deletion'] as const).map((topic) => (
            <details key={topic}>
              <summary>{t(`landing.faq.${topic}.question`)}</summary>
              <p>{t(`landing.faq.${topic}.answer`)}</p>
            </details>
          ))}
        </section>
        <section aria-labelledby="landing-start">
          <h2 id="landing-start">{t('landing.closing')}</h2>
          <Link to="/register">{t('auth.register.heading')}</Link>
        </section>
      </main>
    </div>
  )
}
