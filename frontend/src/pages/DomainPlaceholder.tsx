import { useOutletContext, useParams } from 'react-router-dom'
import type { Domain } from '../layout/AppLayout'

export function DomainPlaceholder() {
  const { code } = useParams()
  // `useOutletContext` is an unchecked cast — react-router validates nothing — so admit the null
  // it hands back outside an Outlet and fall through to the `code` heading below.
  const domains = useOutletContext<Domain[] | null>() ?? []
  const domain = domains.find((candidate) => candidate.code === code)

  return (
    <>
      <h1>{domain?.name_pl ?? code}</h1>
      <p>Ta domena pojawi się w kolejnym wycinku.</p>
    </>
  )
}
