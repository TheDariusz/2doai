import { useOutletContext, useParams } from 'react-router-dom'
import type { Domain } from '../layout/AppLayout'

/** Holding page so the navigation is walkable before the feature slices land. */
export function DomainPlaceholder() {
  const { code } = useParams()
  const domains = useOutletContext<Domain[]>()
  const domain = domains.find((candidate) => candidate.code === code)

  return (
    <>
      <h1>{domain?.name_pl ?? code}</h1>
      <p>Ta domena pojawi się w kolejnym wycinku.</p>
    </>
  )
}
