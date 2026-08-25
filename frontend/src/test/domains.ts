import type { Domain } from '../layout/AppLayout'

/**
 * The 11 seeded categories, verbatim from `V2__seed_categories.sql` (codes are the English
 * `LifeDomain` constants; `name` is the label), in the `display_order` the server sorts by.
 *
 * Shared rather than copied per suite: these codes are already duplicated across the stack with
 * nothing guarding them (lessons.md), so a twelfth domain should cost one edit here, not one per
 * test file that happens to need a category.
 */
export const DOMAINS: Domain[] = [
  { code: 'HEALTH', name: 'Zdrowie' },
  { code: 'FINANCE', name: 'Finanse' },
  { code: 'CAREER', name: 'Kariera i rozwój zawodowy' },
  { code: 'EDUCATION', name: 'Edukacja i rozwój osobisty' },
  { code: 'RELATIONSHIPS', name: 'Relacje' },
  { code: 'HOME', name: 'Dom i otoczenie' },
  { code: 'LEISURE', name: 'Czas wolny i hobby' },
  { code: 'ADMIN', name: 'Sprawy formalne i administracyjne' },
  { code: 'SAFETY', name: 'Bezpieczeństwo i przygotowanie na sytuacje awaryjne' },
  { code: 'TRANSPORT', name: 'Transport i mobilność' },
  { code: 'INNER_GROWTH', name: 'Rozwój wewnętrzny / wartości' },
]
