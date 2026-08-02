import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './client'

const fetchMock = vi.fn()

/** Minimal stand-in for `Response` — the client only reads these four members. */
function response(status: number, body?: unknown) {
  return {
    ok: status < 400,
    status,
    statusText: '',
    json: async () => body,
  }
}

function headersOf(call: number) {
  return fetchMock.mock.calls[call][1].headers as Record<string, string>
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=token-123'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; max-age=0'
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

  it('resolves without parsing a body on 204', async () => {
    const noContent = response(204)
    noContent.json = async () => {
      throw new Error('204 has no body to parse')
    }
    fetchMock.mockResolvedValue(noContent)

    await expect(api('/sessions/current', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
