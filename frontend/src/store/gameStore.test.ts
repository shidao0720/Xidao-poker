import { beforeEach, describe, expect, it } from 'vitest'
import type { GameEvent, GameSnapshot } from '../types/protocol'
import { reduceGameEvent, useGameStore } from './gameStore'

function snapshot(overrides: Partial<GameSnapshot> = {}): GameSnapshot {
  return {
    sessionId: 'room-1',
    ownerId: 'A',
    handId: 1,
    phase: 'PREFLOP',
    buttonSeat: 0,
    smallBlindSeat: 1,
    bigBlindSeat: 2,
    currentActorSeat: 0,
    turnId: 7,
    currentBet: 20,
    minimumRaise: 20,
    pot: 30,
    pots: [],
    communityCards: [],
    players: [
      {
        id: 'A', name: 'Alice', seat: 0, stack: 1_000, streetBet: 0,
        totalContribution: 0, status: 'ACTIVE', inHand: true, canAct: true,
        ready: true, holeCards: [],
      },
      {
        id: 'O', name: 'Observer', seat: 3, stack: 0, streetBet: 0,
        totalContribution: 0, status: 'BUSTED', inHand: false, canAct: false,
        ready: false, holeCards: [],
      },
    ],
    actionOptions: {
      legalActions: ['FOLD', 'CALL', 'RAISE'], toCall: 20, callAmount: 20,
      minimumBetTo: null, minimumRaiseTo: 40, maximumTo: 1_000,
    },
    awards: [],
    lastSequence: 10,
    ...overrides,
  }
}

function event(overrides: Partial<GameEvent> = {}): GameEvent {
  return {
    sequence: 11,
    type: 'PLAYER_ACTION',
    handId: 1,
    playerId: 'A',
    data: { paid: 20, stack: 980, streetBet: 20, currentBet: 20, status: 'ACTIVE', canAct: true },
    ...overrides,
  }
}

describe('game snapshot synchronization', () => {
  beforeEach(() => useGameStore.getState().reset())

  it('replaces the complete snapshot instead of merging stale players', () => {
    useGameStore.getState().replaceSnapshot(snapshot())
    useGameStore.getState().replaceSnapshot(snapshot({ players: [], lastSequence: 30 }))

    expect(useGameStore.getState().snapshot?.players).toEqual([])
    expect(useGameStore.getState().snapshot?.lastSequence).toBe(30)
  })

  it('detects a sequence gap without mutating state', () => {
    useGameStore.getState().replaceSnapshot(snapshot())

    const result = useGameStore.getState().applyEvent(event({ sequence: 13 }))

    expect(result).toBe('gap')
    expect(useGameStore.getState().snapshot?.lastSequence).toBe(10)
  })

  it('projects explicit server action data and preserves observers outside the action queue', () => {
    const next = reduceGameEvent(snapshot(), event())

    expect(next.pot).toBe(50)
    expect(next.currentActorSeat).toBeNull()
    expect(next.actionOptions.legalActions).toEqual([])
    expect(next.players.find((player) => player.id === 'A')).toMatchObject({ stack: 980, streetBet: 20 })
    expect(next.players.find((player) => player.id === 'O')).toMatchObject({ status: 'BUSTED', canAct: false })
  })

  it('marks a disconnected actor unable to act using the server event', () => {
    const next = reduceGameEvent(snapshot(), event({
      type: 'PLAYER_DISCONNECTED',
      data: { seat: 0, forfeitedHand: true },
    }))

    expect(next.players[0]).toMatchObject({ status: 'DISCONNECTED', canAct: false })
  })
})
