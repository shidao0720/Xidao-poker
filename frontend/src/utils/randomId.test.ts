import { describe, expect, it } from 'vitest'
import { createRandomId } from './randomId'

describe('createRandomId', () => {
  it('does not depend on crypto.randomUUID in LAN HTTP contexts', () => {
    const id = createRandomId('p_', (bytes) => {
      bytes.forEach((_, index) => { bytes[index] = index })
    })

    expect(id).toBe('p_000102030405060708090a0b0c0d0e0f')
    expect(id).toMatch(/^[A-Za-z0-9_-]{1,64}$/)
  })

  it('creates a command id within the server envelope limit', () => {
    expect(createRandomId('cmd_')).toMatch(/^cmd_[a-f0-9]{32}$/)
    expect(createRandomId('cmd_').length).toBeLessThanOrEqual(128)
  })
})
