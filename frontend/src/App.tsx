import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppLayout } from './layout/AppLayout'
import { AuthPage } from './pages/AuthPage'
import { DomainPlaceholder } from './pages/DomainPlaceholder'
import { GoalsPage } from './pages/GoalsPage'

/** The route tree, router-free so tests can mount it inside a `MemoryRouter`. */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />

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
