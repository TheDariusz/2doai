import { describe, expect, it } from 'vitest'
import { awaitsCode, CODE_REQUEST_KEY, forgetCodeRequest, rememberCodeRequest } from './codeRequest'

const NOW = Date.parse('2026-11-14T21:00:00Z')
const MINUTE = 60_000

describe('codeRequest', () => {
  it('knows the address this device asked a code for', () => {
    rememberCodeRequest('ala@example.pl', NOW)

    expect(awaitsCode('ala@example.pl', NOW + MINUTE)).toBe(true)
  })

  it('does not vouch for an address this device never asked about', () => {
    rememberCodeRequest('ala@example.pl', NOW)

    expect(awaitsCode('ola@example.pl', NOW)).toBe(false)
  })

  it('compares addresses the way the server does — trimmed, case-insensitive', () => {
    rememberCodeRequest('  Ala@Example.PL ', NOW)

    expect(awaitsCode('ala@example.pl', NOW)).toBe(true)
  })

  /** A code lives 15 minutes (`EmailVerificationService.CODE_VALIDITY`); so does the memory of asking. */
  it('forgets a request once the code it asked for has expired', () => {
    rememberCodeRequest('ala@example.pl', NOW)

    expect(awaitsCode('ala@example.pl', NOW + 15 * MINUTE - 1)).toBe(true)
    expect(awaitsCode('ala@example.pl', NOW + 15 * MINUTE)).toBe(false)
  })

  it('forgets on request', () => {
    rememberCodeRequest('ala@example.pl', NOW)
    forgetCodeRequest()

    expect(awaitsCode('ala@example.pl', NOW)).toBe(false)
  })

  it('treats a garbled entry as no request at all', () => {
    localStorage.setItem(CODE_REQUEST_KEY, '{not json')

    expect(awaitsCode('ala@example.pl', NOW)).toBe(false)
  })
})
