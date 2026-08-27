import { describe, expect, it } from 'vitest'
import { DEV_BACKEND_ORIGIN } from './devProxy'

describe('Vite development proxy', () => {
  it('uses an HTTP origin for WebSocket upgrade rewriting', () => {
    const target = new URL(DEV_BACKEND_ORIGIN)

    expect(target.protocol).toBe('http:')
    expect(target.host).toBe('localhost:8080')
  })
})
