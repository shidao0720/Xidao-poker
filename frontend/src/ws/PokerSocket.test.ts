import { describe, expect, it } from 'vitest'
import { isTerminalConnectionError, reconnectAttemptsExhausted } from './PokerSocket'

describe('PokerSocket connection errors', () => {
  it('stops reconnect loops for stale identity and missing rooms', () => {
    expect(isTerminalConnectionError('STALE_CONNECTION')).toBe(true)
    expect(isTerminalConnectionError('MEMBER_ALREADY_CONNECTED')).toBe(true)
    expect(isTerminalConnectionError('ROOM_NOT_FOUND')).toBe(true)
  })

  it('keeps gameplay validation errors recoverable', () => {
    expect(isTerminalConnectionError('NOT_YOUR_TURN')).toBe(false)
    expect(isTerminalConnectionError('INVALID_AMOUNT')).toBe(false)
  })

  it('bounds repeated handshake failures instead of retrying forever', () => {
    expect(reconnectAttemptsExhausted(7)).toBe(false)
    expect(reconnectAttemptsExhausted(8)).toBe(true)
  })
})
