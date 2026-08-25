import { useOutletContext, useParams } from 'react-router'
import type { Domain } from '../layout/AppLayout'

export function DomainPlaceholder() {
  // The URL spells the code lowercased (`/domain/inner_growth`) — casing belongs to the wire enum,
  // not to a link a user reads, types or shares. The nav lowercases it, this matches it back.
  const { code } = useParams()
  const domain = useOutletContext<Domain[]>().find(
    (candidate) => candidate.code.toLowerCase() === code,
  )

  return (
    <>
      <h1>{domain?.name ?? code}</h1>
      <p>Ta domena pojawi się w kolejnym wycinku.</p>
    </>
  )
}
