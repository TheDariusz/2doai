import { useLocation } from 'react-router'

/** Renders where the router is, so a test can assert on navigation without a real browser. */
export function LocationProbe() {
  const { pathname, search, hash } = useLocation()
  return <p data-testid="location">{pathname + search + hash}</p>
}
