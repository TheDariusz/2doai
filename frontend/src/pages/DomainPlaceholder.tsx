import { useOutletContext, useParams } from 'react-router'
import type { Domain } from '../layout/AppLayout'

export function DomainPlaceholder() {
  const { code } = useParams()
  const domain = useOutletContext<Domain[]>().find((candidate) => candidate.code === code)

  return (
    <>
      <h1>{domain?.name_pl ?? code}</h1>
      <p>Ta domena pojawi się w kolejnym wycinku.</p>
    </>
  )
}
