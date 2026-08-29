import { describe, expect, it } from 'vitest'
import { BUILD_VERSION, PROTOCOL_VERSION, buildVersionsCompatible } from './protocol'

describe('protocol compatibility', () => {
  it('keeps an explicit positive protocol version', () => {
    expect(PROTOCOL_VERSION).toBeGreaterThan(0)
  })

  it('accepts the matching release and development builds', () => {
    expect(buildVersionsCompatible(BUILD_VERSION)).toBe(true)
    expect(buildVersionsCompatible('dev')).toBe(true)
  })

  it('rejects another release build', () => {
    expect(buildVersionsCompatible('999.0.0')).toBe(false)
  })
})
