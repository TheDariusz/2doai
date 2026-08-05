import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './auth-context'

/** Layout route that gates everything nested under it. */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return null
  }
  if (status === 'anonymous') {
    // Carry the attempted location so the login screen can return the user to it.
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}
