import { describe, expect, it } from 'vitest'
import { readVerifyLink } from './verifyLink'

describe('readVerifyLink', () => {
  it('reads the address and the code the mail link carries in its fragment', () => {
    expect(readVerifyLink('#email=ala%40example.pl&code=04231789')).toEqual({
      email: 'ala@example.pl',
      code: '04231789',
    })
  })

  it('keeps a plus in the address, which the mail encodes', () => {
    expect(readVerifyLink('#email=ala%2Btodo%40example.pl&code=04231789').email).toBe(
      'ala+todo@example.pl',
    )
  })

  it('reports what is missing rather than inventing it', () => {
    expect(readVerifyLink('')).toEqual({ email: null, code: null })
    expect(readVerifyLink('#email=ala%40example.pl')).toEqual({ email: 'ala@example.pl', code: null })
  })

  it('drops a code that is not eight digits, so a mangled link never prefills a guess', () => {
    expect(readVerifyLink('#email=ala%40example.pl&code=0423').code).toBeNull()
    expect(readVerifyLink('#email=ala%40example.pl&code=abcdefgh').code).toBeNull()
  })
})
