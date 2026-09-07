import type { Domain } from '../layout/AppLayout'

/**
 * The 11 seeded categories as the server answers an English caller (codes are the `LifeDomain`
 * constants; `name` is the label the request's language picked), in the `display_order` the server
 * sorts by.
 *
 * Shared rather than copied per suite: these codes are already duplicated across the stack with
 * nothing guarding them (lessons.md), so a twelfth domain should cost one edit here, not one per
 * test file that happens to need a category.
 */
export const DOMAINS: Domain[] = [
  { code: 'HEALTH', name: 'Health' },
  { code: 'FINANCE', name: 'Finances' },
  { code: 'CAREER', name: 'Career & professional growth' },
  { code: 'EDUCATION', name: 'Education & personal growth' },
  { code: 'RELATIONSHIPS', name: 'Relationships' },
  { code: 'HOME', name: 'Home & surroundings' },
  { code: 'LEISURE', name: 'Leisure & hobbies' },
  { code: 'ADMIN', name: 'Admin & paperwork' },
  { code: 'SAFETY', name: 'Safety & preparedness' },
  { code: 'TRANSPORT', name: 'Transport & mobility' },
  { code: 'INNER_GROWTH', name: 'Inner growth & values' },
]
