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

/**
 * A pin on a sentence, insensitive to where the hand-wrapped source breaks its lines. Written as a
 * helper after a plain-string pin went red for a paragraph that had only been reflowed: a phrase
 * test must fail when the claim disappears, never when the prose around it is re-indented, or the
 * next person learns to edit the test instead of the page.
 */
function phrase(text) {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+'))
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

test('exported data-model diagrams keep readable light-theme text', () => {
  for (const name of ['data-model-current.svg', 'data-model-target.svg']) {
    const svg = readFileSync(resolve(docsDirectory, `../context/foundation/${name}`), 'utf8')

    assert.match(svg, /<svg\b[^>]*color-scheme:\s*light;/, `${name} should force its light palette`)
    assert.doesNotMatch(
      svg,
      /color-scheme:\s*light dark/,
      `${name} must not choose white text for transparent table bodies`
    )
  }
})

test('Mermaid is pinned and embedded source is valid HTML', () => {
  assert.match(html, /mermaid@11\.16\.0\//)
  assert.doesNotMatch(html, /mermaid@11\//)

  for (const block of matches(/<pre class="mermaid">([\s\S]*?)<\/pre>/g)) {
    assert.doesNotMatch(block[1], /<\|\.\./, 'escape Mermaid inheritance arrows as &lt;|..')
  }
})

test('status language distinguishes what is implemented from the target product', () => {
  // The frontend stopped being a scaffold once routing, the API client, auth and the shell landed.
  assert.doesNotMatch(html, /scaffold/i)
  assert.match(html, phrase('CSRF-aware client'))
  // S-02 shipped the goals/dreams UI, S-07 folded current tasks into the same screen and S-08 added
  // the two filters — so every line calling those planned is gone. This pin was itself the trap
  // CLAUDE.md names: it stayed green while asserting prose the slice had just falsified, so it now
  // pins the two facts that are load-bearing instead — filters exist, and they cost no API change.
  assert.match(html, phrase('narrowed by layer and by category'))
  assert.match(html, phrase('<code>GET /api/goals</code> still publishes no query parameters'))
  assert.match(html, phrase('<code>/goals</code> screen adds the first data UI'))
  // S-07 shipped current tasks as a third `goal` layer, so the one ghosted box is a deferred split,
  // not planned work. Without this the prose can quietly re-promise the table the slice rejected.
  assert.doesNotMatch(html, /current_task/)
  assert.match(html, phrase('deferred split rather than planned work'))
  assert.match(html, phrase('Mutable domain tables use'))
  assert.match(html, phrase('append-only event tables may omit'))
  assert.match(html, phrase('repository snapshot'))
  assert.match(html, /pull-request quality gate/i)
  // S-04b shipped the AI half of the proactive loop, so the two lines that called it future work
  // are gone and the screen it landed on is named. What is still planned is only the timing.
  assert.doesNotMatch(html, phrase('roadmap slices S-04b and S-05'))
  assert.match(html, phrase('<code>POST /api/proposals</code>'))
  assert.match(html, phrase('<code>POST /api/proposals/{id}/answer</code>'))
})

test('target runtime preserves the current deployment backbone and marks planned additions', () => {
  const architecture = html.match(/<section id="architecture">([\s\S]*?)<\/section>/)?.[1] ?? ''
  const target = architecture.match(
    /<h3>Target MVP runtime[\s\S]*?<pre class="mermaid">([\s\S]*?)<\/pre>/
  )?.[1] ?? ''

  for (const currentComponent of [
    'Browser',
    'Cloudflare Pages',
    'Cloudflare reverse proxy',
    'Fly.io',
    'Neon PostgreSQL 18',
    'GitHub Actions'
  ]) {
    assert.match(target, new RegExp(currentComponent), `target omits current component: ${currentComponent}`)
  }
  assert.match(target, /Current deployment backbone/)
  assert.match(target, /AI provider API/)
  assert.match(target, /natural-rhythm/i)
  // The rhythm and its mail transport are drawn, whatever they are called. Pinned this way on
  // purpose: S-05 shipped both, and the two literals that used to stand here — "Planned MVP
  // additions" and "Email delivery provider" — were assertions that the page still called them
  // planned. A pinned phrase can only guard a claim that stays true; naming the components guards
  // the diagram, naming their status guards nothing and eventually contradicts the code.
  assert.match(target, /Resend|SMTP|Email/)
  // Something is still unbuilt, and the legend that says which must survive with it.
  assert.match(target, /classDef planned/)
})

test('backend terminology is defined before the implementation diagrams', () => {
  const backend = html.match(/<section id="backend">([\s\S]*?)<\/section>/)?.[1] ?? ''
  const termsPosition = backend.indexOf('id="backend-terms"')
  const firstDiagramPosition = backend.indexOf('<pre class="mermaid">')

  assert.ok(termsPosition >= 0, 'expected a backend terminology guide')
  assert.ok(termsPosition < firstDiagramPosition, 'backend terms should be defined before diagrams')
  for (const term of ['Account', 'User', 'Session', 'Category', 'Goal', 'Dream', 'Task', 'Proposal', 'AI memory', 'Profile fact', 'Episode']) {
    assert.match(backend, new RegExp(`<dt>${term}</dt>`), `missing backend definition: ${term}`)
  }
})

test('backend class diagrams identify ports, adapters, and application roles', () => {
  const backend = html.match(/<section id="backend">([\s\S]*?)<\/section>/)?.[1] ?? ''
  const legendPosition = backend.indexOf('id="architecture-role-legend"')
  const firstDiagramPosition = backend.indexOf('<pre class="mermaid">')

  assert.ok(legendPosition >= 0, 'expected a ports and adapters legend')
  assert.ok(legendPosition < firstDiagramPosition, 'architectural roles should be explained before diagrams')
  for (const role of [
    'Inbound adapter',
    'Application service',
    'Outbound port',
    'Outbound adapter',
    'Persistence adapter',
    'Extension port'
  ]) {
    assert.match(backend, new RegExp(`<dt>${role}</dt>`), `missing architectural role: ${role}`)
  }

  assert.match(backend, /class UserController \{\s+&lt;&lt;inbound adapter \/ REST&gt;&gt;/)
  assert.match(backend, /class RegistrationService \{\s+&lt;&lt;application service&gt;&gt;/)
  assert.match(backend, /class LlmClient \{\s+&lt;&lt;outbound port&gt;&gt;/)
  assert.match(backend, /class SpringAiLlmClient \{\s+&lt;&lt;outbound adapter&gt;&gt;/)
  assert.match(backend, /class PerUserDataDeleter \{\s+&lt;&lt;extension port&gt;&gt;/)
  assert.match(backend, /&lt;&lt;persistence adapter \/ Spring Data&gt;&gt;/)
})

test('AI communication chapter explains the implemented Spring pipeline and names its real caller', () => {
  const ai = html.match(/<section id="ai-communication">([\s\S]*?)<\/section>/)?.[1] ?? ''

  assert.ok(ai, 'expected a dedicated AI communication chapter')
  // Until S-04b this chapter asserted that nothing called the port — and this test pinned that
  // sentence, so the gate stayed green the moment `ProposalService` made it false. The pin now
  // holds the two facts that would go stale the same way: there IS a production caller, and it
  // owns the failure path, which is the whole reason the port has no fallback of its own.
  assert.doesNotMatch(ai, /no production (?:use case|controller)[\s\S]{0,200}calls?\s+<code>LlmClient<\/code>/i)
  assert.match(ai, phrase('<code>ProposalService</code> is the first production caller'))
  assert.match(ai, /template fallback|falls back to a deterministic template/i)

  for (const term of [
    'LlmClient',
    'SpringAiLlmClient',
    'ChatModel',
    'OpenRouter',
    'OPENROUTER_API_KEY',
    'completeStructured',
    'LlmException'
  ]) {
    assert.match(ai, new RegExp(term), `AI communication chapter is missing: ${term}`)
  }

  assert.match(ai, /spring-ai-starter-model-openai/)
  assert.match(ai, /provider[\s\S]*data_collection[\s\S]*deny/)
  assert.match(ai, /60s/)
  assert.match(ai, /429\/5xx/)
  assert.match(ai, /OpenRouterLiveTest/)
  assert.match(ai, /sequenceDiagram/)
})
