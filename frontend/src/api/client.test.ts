import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './client'
import { response } from '../test/auth'

const fetchMock = vi.fn()

function headersOf(call: number) {
  return fetchMock.mock.calls[call][1].headers as Record<string, string>
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

describe('api client', () => {
  it('omits the CSRF header on safe requests', async () => {
    fetchMock.mockResolvedValue(response(200, { id: 'u1', email: 'a@b.pl' }))

    await expect(api('/users/me')).resolves.toEqual({ id: 'u1', email: 'a@b.pl' })

    expect(fetchMock).toHaveBeenCalledWith('/api/users/me', expect.anything())
    expect(headersOf(0)).not.toHaveProperty('X-XSRF-TOKEN')
  })

  it('echoes the XSRF-TOKEN cookie as X-XSRF-TOKEN on mutations', async () => {
    fetchMock.mockResolvedValue(response(201, { id: 'u1', email: 'a@b.pl' }))

    await api('/sessions', { method: 'POST', body: { email: 'a@b.pl', password: 'secret12' } })

    expect(headersOf(0)['X-XSRF-TOKEN']).toBe('token-123')
    expect(fetchMock.mock.calls[0][1].credentials).toBe('include')
  })

  it('surfaces the status and the Problem JSON detail as an ApiError', async () => {
    fetchMock.mockResolvedValue(response(401, { detail: 'Authentication is required or credentials are invalid' }))

    await expect(api('/users/me')).rejects.toMatchObject({
      status: 401,
      message: 'Authentication is required or credentials are invalid',
    })
    await expect(api('/users/me')).rejects.toBeInstanceOf(ApiError)
  })

  it('carries the Problem JSON type so callers can tell two 403s apart', async () => {
    fetchMock.mockResolvedValue(
      response(403, { type: 'urn:2doai:problem:re-auth-failed', detail: 'The password you entered is incorrect' }),
    )

    await expect(api('/users/me', { method: 'DELETE', body: { password: 'zle' } })).rejects.toMatchObject({
      status: 403,
      type: 'urn:2doai:problem:re-auth-failed',
    })
  })

  it('leaves the type undefined when the body carries none', async () => {
    fetchMock.mockResolvedValue(response(403, { detail: 'The authenticated request is not allowed' }))

    await expect(api('/users/me', { method: 'DELETE', body: {} })).rejects.toMatchObject({
      status: 403,
      type: undefined,
    })
  })

  it('refuses a mutation instead of sending one the server is bound to reject', async () => {
    // No XSRF-TOKEN cookie yet — e.g. a login submitted before the bootstrap GET primed it.
    document.cookie = 'XSRF-TOKEN=; max-age=0'

    await expect(api('/sessions', { method: 'POST', body: {} })).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('announces a 401 so the app can drop to anonymous from anywhere', async () => {
    fetchMock.mockResolvedValue(response(401, { detail: 'Authentication is required' }))
    const onExpired = vi.fn()
    window.addEventListener('session-expired', onExpired)

    await expect(api('/categories')).rejects.toBeInstanceOf(ApiError)

    expect(onExpired).toHaveBeenCalled()
    window.removeEventListener('session-expired', onExpired)
  })

  it('falls back to the status when the body is not Problem JSON', async () => {
    // A Cloudflare or Fly HTML error page — and `statusText` is always '' over HTTP/2.
    const htmlError = response(502)
    htmlError.json = async () => {
      throw new Error('not JSON')
    }
    fetchMock.mockResolvedValue(htmlError)

    await expect(api('/categories')).rejects.toMatchObject({ status: 502, message: 'HTTP 502' })
  })

  it('resolves without parsing a body on 204', async () => {
    const noContent = response(204)
    noContent.json = async () => {
      throw new Error('204 has no body to parse')
    }
    fetchMock.mockResolvedValue(noContent)

    await expect(api('/sessions/current', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
