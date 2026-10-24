import type { ReactNode } from 'react'

/**
 * The screen's icons, drawn rather than installed: a few stroke paths are smaller than any icon
 * package and carry no version to keep. They are decoration in every case — the control around them
 * always has a name of its own — so each one is `aria-hidden` and none of them takes a label.
 *
 * `currentColor` throughout: the colour is the button's state (muted, hovered, filled), and an icon
 * that carried its own would have to be told about every one of them.
 */
function Icon({ size = 16, filled = false, children }: { size?: number; filled?: boolean; children: ReactNode }) {
  // A brand mark is a filled silhouette, not strokes; it still takes its colour from the control.
  const paint = filled
    ? { fill: 'currentColor' }
    : { fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round' } as const
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" {...paint}>
      {children}
    </svg>
  )
}

/** The quick-add bar's mark: what the bar is for, before anything is typed into it. */
export function PlusIcon() {
  return (
    <Icon size={18}>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </Icon>
  )
}

/** Inside the complete circle, and only once the entry is done. */
export function CheckIcon() {
  return (
    <Icon size={14}>
      <path d="m5 12 5 5L20 7" />
    </Icon>
  )
}

export function PencilIcon() {
  return (
    <Icon>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </Icon>
  )
}

export function TrashIcon() {
  return (
    <Icon>
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
    </Icon>
  )
}

/** The proposal's eyebrow: the one place on the screen where the app is doing the talking. */
export function SparkleIcon() {
  return (
    <Icon>
      <path d="M12 3v3" />
      <path d="M12 18v3" />
      <path d="M3 12h3" />
      <path d="M18 12h3" />
      <path d="m5.6 5.6 2.2 2.2" />
      <path d="m16.2 16.2 2.2 2.2" />
      <path d="m5.6 18.4 2.2-2.2" />
      <path d="m16.2 7.8 2.2-2.2" />
    </Icon>
  )
}

/** The theme switch's three options; the button carries the name. */
export function SunIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2" />
      <path d="M12 20v2" />
      <path d="M2 12h2" />
      <path d="M20 12h2" />
      <path d="m4.9 4.9 1.4 1.4" />
      <path d="m17.7 17.7 1.4 1.4" />
      <path d="m4.9 19.1 1.4-1.4" />
      <path d="m17.7 6.3 1.4-1.4" />
    </Icon>
  )
}

export function MoonIcon() {
  return (
    <Icon>
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
    </Icon>
  )
}

/** Half light, half dark: whichever the OS says. */
export function AutoThemeIcon() {
  return (
    <Icon>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3a9 9 0 0 1 0 18Z" fill="currentColor" />
    </Icon>
  )
}

/** The landing footer's profile links, brand marks as filled shapes; the link around each carries its name. */
export function GitHubIcon() {
  return (
    <Icon size={20} filled>
      <path d="M12 .5A12 12 0 0 0 8.2 23.9c.6.1.8-.3.8-.6v-2.2c-3.3.7-4-1.4-4-1.4-.6-1.4-1.4-1.8-1.4-1.8-1.1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1.1 1.8 2.8 1.3 3.5 1 .1-.8.4-1.3.8-1.6-2.7-.3-5.5-1.3-5.5-5.9 0-1.3.5-2.4 1.2-3.2-.1-.3-.5-1.5.1-3.2 0 0 1-.3 3.3 1.2a11.5 11.5 0 0 1 6 0c2.3-1.5 3.3-1.2 3.3-1.2.6 1.7.2 2.9.1 3.2.8.8 1.2 1.9 1.2 3.2 0 4.6-2.8 5.6-5.5 5.9.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A12 12 0 0 0 12 .5Z" />
    </Icon>
  )
}

export function LinkedInIcon() {
  return (
    <Icon size={20} filled>
      <path d="M20.5 2h-17A1.5 1.5 0 0 0 2 3.5v17A1.5 1.5 0 0 0 3.5 22h17a1.5 1.5 0 0 0 1.5-1.5v-17A1.5 1.5 0 0 0 20.5 2ZM8 19H5v-9h3Zm-1.5-10.3a1.75 1.75 0 1 1 0-3.5 1.75 1.75 0 0 1 0 3.5ZM19 19h-3v-4.7c0-1.4-.5-2.1-1.6-2.1-1.2 0-1.9.8-1.9 2.1V19h-3v-9h2.9v1.3a3.4 3.4 0 0 1 3-1.6c2.2 0 3.6 1.3 3.6 4Z" />
    </Icon>
  )
}

/** Trails the landing's call to action and nudges forward under the pointer (index.css). */
export function ArrowIcon() {
  return (
    <Icon size={18}>
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </Icon>
  )
}

/**
 * One mark per step of the landing's "how it works": write it down, return to it, take a step. The
 * list item carries the number and the heading; this is only its picture.
 */
export function StepIcon({ step }: { step: 'capture' | 'revisit' | 'start' }) {
  return (
    <span className="step-icon">
      <Icon size={24}>
        {step === 'capture' && (
          <>
            <rect x="3" y="3" width="18" height="18" rx="5" />
            <path d="M12 8v8" />
            <path d="M8 12h8" />
          </>
        )}
        {step === 'revisit' && (
          <>
            <path d="M3 12a9 9 0 1 0 3-6.7" />
            <path d="M3 4v5h5" />
            <path d="M12 8v4l3 2" />
          </>
        )}
        {step === 'start' && (
          <>
            <path d="M4 20 20 4" />
            <path d="M14 4h6v6" />
            <path d="M4 14v6h6" />
          </>
        )}
      </Icon>
    </span>
  )
}
