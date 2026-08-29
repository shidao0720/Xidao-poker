import { describe, expect, it } from 'vitest'
import type { PlayerSnapshot } from '../types/protocol'
import { positionPlayersForViewer } from './seatLayout'

function player(id: string, seat: number): PlayerSnapshot {
  return {
    id,
    name: id,
    seat,
    stack: 1_000,
    streetBet: 0,
    totalContribution: 0,
    status: 'ACTIVE',
    connectionStatus: 'CONNECTED',
    seatStatus: 'SEATED',
    handStatus: 'ACTIVE',
    inHand: true,
    canAct: true,
    ready: true,
    holeCards: [],
  }
}

describe('positionPlayersForViewer', () => {
  it('places the viewer at the bottom and preserves clockwise seat order', () => {
    const players = [player('seat-1', 1), player('viewer', 7), player('seat-9', 9)]

    const positioned = positionPlayersForViewer(players, 'viewer')

    expect(positioned.map(({ player: current }) => current.id)).toEqual([
      'viewer',
      'seat-9',
      'seat-1',
    ])
    expect(positioned[0]?.position.x).toBe(50)
    expect(positioned[0]?.position.y).toBe(91)
  })

  it('places a heads-up opponent at the top of the table', () => {
    const positioned = positionPlayersForViewer(
      [player('opponent', 2), player('viewer', 6)],
      'viewer',
    )

    expect(positioned[0]?.position).toEqual({ x: 50, y: 91 })
    expect(positioned[1]?.position).toEqual({ x: 50, y: 9 })
  })

  it('does not mutate the snapshot player order', () => {
    const players = [player('later', 8), player('viewer', 3), player('earlier', 1)]
    const originalOrder = players.map((current) => current.id)

    positionPlayersForViewer(players, 'viewer')

    expect(players.map((current) => current.id)).toEqual(originalOrder)
  })

  it('distributes ten players across ten distinct positions', () => {
    const players = Array.from({ length: 10 }, (_, seat) => player(`player-${seat}`, seat))

    const positioned = positionPlayersForViewer(players, 'player-6')
    const uniquePositions = new Set(
      positioned.map(({ position }) => `${position.x},${position.y}`),
    )

    expect(positioned).toHaveLength(10)
    expect(uniquePositions.size).toBe(10)
    expect(positioned[0]?.player.id).toBe('player-6')
    expect(positioned[0]?.position).toEqual({ x: 50, y: 91 })
  })
})
