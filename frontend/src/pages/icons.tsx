import type { ReactNode } from 'react'

/**
 * The screen's icons, drawn rather than installed: five stroke paths are smaller than any icon
 * package and carry no version to keep. They are decoration in every case — the control around them
 * always has a name of its own — so each one is `aria-hidden` and none of them takes a label.
 *
 * `currentColor` throughout: the colour is the button's state (muted, hovered, filled), and an icon
 * that carried its own would have to be told about every one of them.
 */
function Icon({ size = 16, children }: { size?: number; children: ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
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
