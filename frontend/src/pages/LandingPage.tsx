import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { LanguageSwitch } from '../i18n/LanguageSwitch'
import { Wordmark } from '../layout/Wordmark'
import { ThemeSwitch } from '../ThemeSwitch'
import { ArrowIcon, GitHubIcon, LinkedInIcon, PlusIcon, SparkleIcon, StepIcon } from './icons'

const AUTHOR_SITE = 'https://thedariusz.dev'
// `noopener` only: the footer is the author's advert, so the referrer is the point, not a leak.
const EXTERNAL = { target: '_blank', rel: 'noopener' } as const

const LAYERS = ['TASK', 'GOAL', 'DREAM'] as const
const STEPS = ['capture', 'revisit', 'start'] as const
const ANSWERS = ['start', 'later', 'never'] as const
const TOPICS = ['account', 'suggestions', 'control', 'deletion'] as const

/**
 * The headline enters one word at a time, so each word is wrapped for the animation; the spaces
 * stay as text between them, directly in the heading (a wrapper around word and space would lose
 * the space from the accessible name), so the heading reads as the one sentence it is. The last
 * word is the one the page is about and carries the highlight — a second animation, so its inline
 * delay lists the sheen's own as well (one value would be repeated over both).
 */
function Headline({ text }: { text: string }) {
  const words = text.split(' ')
  return (
    <>
      {words.map((word, index) => {
        const last = index === words.length - 1
        const delay = `${0.05 + index * 0.08}s`
        return (
          <Fragment key={index}>
            <span className={last ? 'landing-word landing-word-accent' : 'landing-word'} style={{ animationDelay: last ? `${delay}, 1.6s` : delay }}>
              {word}
            </span>
            {last ? null : ' '}
          </Fragment>
        )
      })}
    </>
  )
}

export function LandingPage() {
  const { status } = useAuth()
  const { t, i18n } = useTranslation()

  if (status === 'authenticated') return <Navigate to="/goals" replace />

  return (
    <div className="landing">
      {/* Three soft lights drifting over a dot grid: the page breathes, and nothing here is read aloud. */}
      <div className="landing-ambient" aria-hidden="true">
        <span className="landing-orb landing-orb-a" />
        <span className="landing-orb landing-orb-b" />
        <span className="landing-orb landing-orb-c" />
        <span className="landing-dots" />
      </div>
      <header className="landing-header">
        <Wordmark />
        <div className="landing-controls">
          <LanguageSwitch onSelect={(language) => void i18n.changeLanguage(language)} />
          <ThemeSwitch />
        </div>
        <Link className="text-link" to="/login">{t('auth.login.heading')}</Link>
      </header>
      <main>
        <section className="landing-hero" aria-labelledby="landing-title">
          <div className="landing-pitch">
            <p className="landing-eyebrow landing-rise"><span className="landing-live" aria-hidden="true" />{t('landing.eyebrow')}</p>
            <h1 id="landing-title"><Headline text={t('landing.title')} /></h1>
            <p className="landing-rise landing-rise-2">{t('landing.intro')}</p>
            <div className="landing-actions landing-rise landing-rise-3">
              <Link className="cta" to="/register">{t('auth.register.heading')}<ArrowIcon /></Link>
              <a className="text-link" href="#how-it-works">{t('landing.seeHow')}</a>
            </div>
          </div>
          {/* The example tilts towards the reader and straightens under the pointer; inside it the
              rows arrive, then a loop plays the product story: a task gets ticked, the dream wakes,
              and the AI's suggestion arrives with its three answers. */}
          <div className="landing-stage landing-rise landing-rise-1">
            <figure className="landing-preview" aria-labelledby="landing-example">
              <figcaption id="landing-example">{t('landing.example')}</figcaption>
              <h2>{t('goals.title')}</h2>
              {LAYERS.map((layer) => (
                <section key={layer} className={`preview-row preview-${layer.toLowerCase()}`}>
                  <span className="preview-box" aria-hidden="true" />
                  <h3>{t(`goals.sections.${layer}`)}</h3>
                  <p>{t(`landing.preview.${layer}`)}</p>
                </section>
              ))}
              <span className="preview-cord" aria-hidden="true" />
              <section className="preview-suggestion">
                <h3><SparkleIcon />{t('landing.preview.suggestion')}</h3>
                <p>{t('landing.preview.message')}</p>
                <p>{t('landing.preview.choices')}</p>
                <ul className="preview-answers">
                  {ANSWERS.map((answer) => (
                    <li key={answer} className={answer === 'start' ? 'preview-answer accent' : 'preview-answer'}>{t(`landing.preview.answers.${answer}`)}</li>
                  ))}
                </ul>
              </section>
            </figure>
          </div>
        </section>
        <section id="how-it-works" aria-labelledby="landing-how">
          <h2 id="landing-how" className="landing-reveal">{t('landing.howTitle')}</h2>
          <ol className="landing-steps">
            {STEPS.map((step) => (
              <li key={step} className="landing-reveal">
                <StepIcon step={step} />
                <h3>{t(`landing.how.${step}.title`)}</h3>
                <p>{t(`landing.how.${step}.body`)}</p>
              </li>
            ))}
          </ol>
        </section>
        <section className="landing-faq" aria-labelledby="landing-faq">
          <h2 id="landing-faq" className="landing-reveal">{t('landing.faqTitle')}</h2>
          <div className="landing-reveal">
            {TOPICS.map((topic) => (
              <details key={topic}>
                <summary>{t(`landing.faq.${topic}.question`)}<span className="faq-mark" aria-hidden="true"><PlusIcon /></span></summary>
                <p>{t(`landing.faq.${topic}.answer`)}</p>
              </details>
            ))}
          </div>
        </section>
        <section className="landing-closing" aria-labelledby="landing-start">
          <span className="landing-orb landing-orb-closing" aria-hidden="true" />
          <h2 id="landing-start" className="landing-reveal">{t('landing.closing')}</h2>
          <div className="landing-reveal">
            <Link className="cta" to="/register">{t('auth.register.heading')}<ArrowIcon /></Link>
          </div>
        </section>
      </main>
      <footer className="landing-footer">
        <div className="landing-author">
          {/* The author's mark, redrawn for a light page in the page's own tokens (index.css); the dark
              original would sit as a black square here. The dot is the one brand colour it keeps. The
              rect is inset by half its stroke so the border is not clipped by the viewBox, and the
              text is pinned to a width so an unknown fallback font cannot run it into the edge. */}
          <svg className="author-mark" viewBox="0 0 32 32" aria-hidden="true">
            <rect x="0.5" y="0.5" width="31" height="31" rx="6" />
            <circle cx="5.5" cy="21" r="2" fill="#e11d48" />
            <text x="10" y="23" textLength="19" lengthAdjust="spacingAndGlyphs">TD</text>
          </svg>
          <p>
            <span>{t('landing.footer.credit')}</span>
            <strong><a href={AUTHOR_SITE} {...EXTERNAL}>TheDariusz</a> · {t('landing.footer.name')}</strong>
            <span>{t('landing.footer.role')}</span>
          </p>
        </div>
        <nav className="landing-social" aria-label={t('landing.footer.links')}>
          <a className="text-link" href={AUTHOR_SITE} {...EXTERNAL}>thedariusz.dev</a>
          <a href="https://github.com/thedariusz" aria-label="GitHub" {...EXTERNAL}><GitHubIcon /></a>
          <a href="https://www.linkedin.com/in/dariusz-szczepanski-dev" aria-label="LinkedIn" {...EXTERNAL}><LinkedInIcon /></a>
        </nav>
      </footer>
    </div>
  )
}
