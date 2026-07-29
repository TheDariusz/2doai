import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const docsDirectory = dirname(fileURLToPath(import.meta.url))
const html = readFileSync(resolve(docsDirectory, 'index.html'), 'utf8')

function matches(pattern) {
  return [...html.matchAll(pattern)]
}

test('navigation targets existing sections', () => {
  const sectionIds = new Set(matches(/<section\b[^>]*\bid="([^"]+)"/g).map((match) => match[1]))
  const nav = html.match(/<nav\b[\s\S]*?<\/nav>/)?.[0] ?? ''
  const navTargets = [...nav.matchAll(/<a\b[^>]*href="#([^"]+)"/g)].map((match) => match[1])

  assert.ok(navTargets.length > 0, 'expected documentation navigation links')
  for (const target of navTargets) {
    assert.ok(sectionIds.has(target), `navigation target #${target} has no matching section`)
  }
})

test('local links and images resolve from the docs directory', () => {
  const urls = matches(/\b(?:href|src)="([^"]+)"/g).map((match) => match[1])
  const localUrls = urls.filter((url) =>
    !url.startsWith('#') &&
    !url.startsWith('http://') &&
    !url.startsWith('https://') &&
    !url.startsWith('mailto:') &&
    !url.startsWith('data:')
  )

  for (const url of localUrls) {
    const path = resolve(docsDirectory, decodeURIComponent(url.split(/[?#]/, 1)[0]))
    assert.ok(existsSync(path), `local reference does not exist: ${url}`)
  }
})

test('content remains available without JavaScript', () => {
  assert.doesNotMatch(html, /<section\b[^>]*\bhidden\b/)
  assert.doesNotMatch(html, /section\[hidden\]/)
  assert.match(html, /<noscript>/)
})

test('page exposes accessible navigation and content structure', () => {
  assert.match(html, /class="skip-link"[^>]*href="#main-content"/)
  assert.match(html, /<nav\b[^>]*aria-label="Documentation sections"/)
  assert.match(html, /<main\b[^>]*id="main-content"/)
  assert.match(html, /:focus-visible/)

  const tables = matches(/<table\b/g).length
  assert.ok(tables > 0, 'expected documentation tables')
  assert.equal(matches(/<thead\b/g).length, tables, 'every table should have a thead')
  assert.equal(matches(/<tbody\b/g).length, tables, 'every table should have a tbody')
})

test('layout and diagrams have narrow-screen and failure fallbacks', () => {
  assert.match(html, /@media\s*\(max-width:\s*760px\)/)
  assert.match(html, /class="diagram-fallback"/)

  const diagramImages = matches(/<img\b[^>]*class="diagram"[^>]*>/g).map((match) => match[0])
  assert.ok(diagramImages.length > 0, 'expected exported data-model diagrams')
  for (const image of diagramImages) {
    assert.match(image, /\bwidth="\d+"/)
    assert.match(image, /\bheight="\d+"/)
    assert.match(image, /\bloading="lazy"/)
  }
})

test('Mermaid is pinned and embedded source is valid HTML', () => {
  assert.match(html, /mermaid@11\.16\.0\//)
  assert.doesNotMatch(html, /mermaid@11\//)

  for (const block of matches(/<pre class="mermaid">([\s\S]*?)<\/pre>/g)) {
    assert.doesNotMatch(block[1], /<\|\.\./, 'escape Mermaid inheritance arrows as &lt;|..')
  }
})

test('status language distinguishes the current scaffold from the target product', () => {
  assert.match(html, /React\/Vite scaffold/)
  assert.match(html, /Product UI, API consumption, and PWA behavior are planned/)
  assert.match(html, /Mutable domain tables use/)
  assert.match(html, /append-only event tables may omit/)
  assert.match(html, /repository snapshot/)
  assert.match(html, /no pull-request\s+validation workflow/i)
})
