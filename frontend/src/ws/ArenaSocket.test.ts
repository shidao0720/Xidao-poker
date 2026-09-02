import { describe, expect, it } from 'vitest'
import { arenaCloseMessage, isTerminalArenaClose } from './ArenaSocket'

describe('ArenaSocket close handling', () => {
  it('stops reconnecting when another page replaces the same player connection', () => {
    expect(isTerminalArenaClose(4001)).toBe(true)
    expect(arenaCloseMessage(4001, 'connection replaced')).toContain('另一个页面或设备')
  })

  it('keeps retrying ordinary transient network closes', () => {
    expect(isTerminalArenaClose(1006)).toBe(false)
    expect(isTerminalArenaClose(1011)).toBe(false)
  })

  it('shows the policy rejection reason without retrying forever', () => {
    expect(isTerminalArenaClose(1008)).toBe(true)
    expect(arenaCloseMessage(1008, 'refresh the page')).toContain('refresh the page')
  })
})
