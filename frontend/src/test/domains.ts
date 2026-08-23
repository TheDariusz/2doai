import type { Domain } from '../layout/AppLayout'

/**
 * The 11 seeded categories, verbatim from `V2__seed_categories.sql` (codes are the English
 * `LifeDomain` constants; `name_pl` is the label), in the `display_order` the server sorts by.
 *
 * Shared rather than copied per suite: these codes are already duplicated across the stack with
 * nothing guarding them (lessons.md), so a twelfth domain should cost one edit here, not one per
 * test file that happens to need a category.
 */
export const DOMAINS: Domain[] = [
  { code: 'HEALTH', name_pl: 'Zdrowie' },
  { code: 'FINANCE', name_pl: 'Finanse' },
  { code: 'CAREER', name_pl: 'Kariera i rozwój zawodowy' },
  { code: 'EDUCATION', name_pl: 'Edukacja i rozwój osobisty' },
  { code: 'RELATIONSHIPS', name_pl: 'Relacje' },
  { code: 'HOME', name_pl: 'Dom i otoczenie' },
  { code: 'LEISURE', name_pl: 'Czas wolny i hobby' },
  { code: 'ADMIN', name_pl: 'Sprawy formalne i administracyjne' },
  { code: 'SAFETY', name_pl: 'Bezpieczeństwo i przygotowanie na sytuacje awaryjne' },
  { code: 'TRANSPORT', name_pl: 'Transport i mobilność' },
  { code: 'INNER_GROWTH', name_pl: 'Rozwój wewnętrzny / wartości' },
]
