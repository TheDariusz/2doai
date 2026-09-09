import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './layout/AppLayout'
import { AuthPage } from './pages/AuthPage'
import { DomainPlaceholder } from './pages/DomainPlaceholder'
import { GoalsPage } from './pages/GoalsPage'
import { VerifyPage } from './pages/VerifyPage'

/** The route tree, router-free so tests can mount it inside a `MemoryRouter`. */
export function AppRoutes() {
  return (
    <Routes>
      {/* Keyed, because these two are one component in two modes at the same position in the tree:
          without distinct keys React reuses the instance across the hop, and the 409 the sign-up
          just raised is still on screen over a sign-in form nobody has submitted. */}
      <Route path="/login" element={<AuthPage key="login" mode="login" />} />
      <Route path="/register" element={<AuthPage key="register" mode="register" />} />
      {/* Public beside the other two: the account it acts on exists but cannot log in yet. */}
      <Route path="/verify" element={<VerifyPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          {/* There is one screen to be on, so the index route is a redirect rather than a page
              of advice about a rail the user is already looking at. */}
          <Route index element={<Navigate to="/goals" replace />} />
          <Route path="goals" element={<GoalsPage />} />
          <Route path="domain/:code" element={<DomainPlaceholder />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}
