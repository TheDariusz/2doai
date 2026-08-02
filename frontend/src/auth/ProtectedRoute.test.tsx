import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ProtectedRoute } from './ProtectedRoute'
import { renderWithAuth, stubAuth } from '../test/auth'
import { type Auth } from './auth-context'

function renderAt(path: string, auth: Auth) {
  renderWithAuth(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="/tajne" element={<p>zawartość dla zalogowanych</p>} />
      </Route>
      <Route path="/login" element={<p>ekran logowania</p>} />
    </Routes>,
    { path, auth },
  )
}

describe('ProtectedRoute', () => {
  it('redirects an anonymous visitor to /login', () => {
    renderAt('/tajne', stubAuth({ status: 'anonymous' }))

    expect(screen.getByText('ekran logowania')).toBeInTheDocument()
    expect(screen.queryByText('zawartość dla zalogowanych')).not.toBeInTheDocument()
  })

  it('renders nothing while the session is still being bootstrapped', () => {
    renderAt('/tajne', stubAuth({ status: 'loading' }))

    expect(screen.queryByText('ekran logowania')).not.toBeInTheDocument()
    expect(screen.queryByText('zawartość dla zalogowanych')).not.toBeInTheDocument()
  })

  it('lets an authenticated user through', () => {
    renderAt('/tajne', stubAuth({ status: 'authenticated', user: { id: 'u1', email: 'a@b.pl' } }))

    expect(screen.getByText('zawartość dla zalogowanych')).toBeInTheDocument()
  })
})
