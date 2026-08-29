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
        totalContribution: 0, status: 'ACTIVE', connectionStatus: 'CONNECTED',
        seatStatus: 'SEATED', handStatus: 'ACTIVE', inHand: true, canAct: true,
        ready: true, holeCards: [],
      },
      {
        id: 'O', name: 'Observer', seat: 3, stack: 0, streetBet: 0,
        totalContribution: 0, status: 'BUSTED', connectionStatus: 'CONNECTED',
        seatStatus: 'BUSTED', handStatus: 'NOT_IN_HAND', inHand: false, canAct: false,
        ready: false, holeCards: [],
      },
    ],
    actionOptions: {
      legalActions: ['FOLD', 'CALL', 'RAISE'], toCall: 20, callAmount: 20,
      minimumBetTo: null, minimumRaiseTo: 40, maximumTo: 1_000,
    },
    awards: [],
    revealedHands: [],
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

  it('normalizes snapshots from an older backend that omit revealedHands', () => {
    const legacySnapshot = snapshot()
    delete (legacySnapshot as Partial<GameSnapshot>).revealedHands

    useGameStore.getState().replaceSnapshot(legacySnapshot)

    expect(useGameStore.getState().snapshot?.revealedHands).toEqual([])
  })

  it('detects a sequence gap without mutating state', () => {
    useGameStore.getState().replaceSnapshot(snapshot())

    const result = useGameStore.getState().applyEvent(event({ sequence: 13 }))

    expect(result).toBe('gap')
    expect(useGameStore.getState().snapshot?.lastSequence).toBe(10)
  })

  it('adds a newly joined player from the realtime event without requiring a refresh snapshot', () => {
    const initial = snapshot({ phase: 'READY', players: [snapshot().players[0]!], lastSequence: 10 })
    useGameStore.getState().replaceSnapshot(initial)

    const result = useGameStore.getState().applyEvent(event({
      type: 'PLAYER_JOINED',
      playerId: 'B',
      data: {
        name: 'Bob',
        seat: 1,
        stack: 1_000,
        streetBet: 0,
        totalContribution: 0,
        status: 'ACTIVE',
        connectionStatus: 'CONNECTED',
        seatStatus: 'SEATED',
        handStatus: 'NOT_IN_HAND',
        inHand: false,
        canAct: false,
        ready: false,
        phase: 'WAITING',
      },
    }))

    expect(result).toBe('applied')
    expect(useGameStore.getState().snapshot?.phase).toBe('WAITING')
    expect(useGameStore.getState().snapshot?.players).toHaveLength(2)
    expect(useGameStore.getState().snapshot?.players[1]).toEqual({
      id: 'B',
      name: 'Bob',
      seat: 1,
      stack: 1_000,
      streetBet: 0,
      totalContribution: 0,
      status: 'ACTIVE',
      connectionStatus: 'CONNECTED',
      seatStatus: 'SEATED',
      handStatus: 'NOT_IN_HAND',
      inHand: false,
      canAct: false,
      ready: false,
      holeCards: [],
    })
  })

  it('projects explicit server action data and preserves observers outside the action queue', () => {
    const next = reduceGameEvent(snapshot(), event())

    expect(next.pot).toBe(50)
    expect(next.currentActorSeat).toBeNull()
    expect(next.actionOptions.legalActions).toEqual([])
    expect(next.players.find((player) => player.id === 'A')).toMatchObject({ stack: 980, streetBet: 20 })
    expect(next.players.find((player) => player.id === 'O')).toMatchObject({ status: 'BUSTED', canAct: false })
  })

  it('keeps all-in hand state orthogonal when the player disconnects', () => {
    const next = reduceGameEvent(snapshot(), event({
      type: 'PLAYER_DISCONNECTED',
      data: { seat: 0, forfeitedHand: false, handStatus: 'ALL_IN' },
    }))

    expect(next.players[0]).toMatchObject({
      status: 'DISCONNECTED',
      connectionStatus: 'DISCONNECTED',
      seatStatus: 'SEATED',
      handStatus: 'ALL_IN',
      canAct: false,
    })
  })

  it('stores the server-authored showdown category without evaluating cards in the client', () => {
    const revealedHands = [{
      playerId: 'A',
      showdown: true,
      category: 'STRAIGHT' as const,
      holeCards: [
        { rank: 'ACE' as const, suit: 'SPADES' as const },
        { rank: 'KING' as const, suit: 'HEARTS' as const },
      ],
      bestCards: [
        { rank: 'ACE' as const, suit: 'SPADES' as const },
        { rank: 'KING' as const, suit: 'HEARTS' as const },
        { rank: 'QUEEN' as const, suit: 'CLUBS' as const },
        { rank: 'JACK' as const, suit: 'DIAMONDS' as const },
        { rank: 'TEN' as const, suit: 'SPADES' as const },
      ],
    }]
    const next = reduceGameEvent(snapshot(), event({
      type: 'SETTLEMENT',
      data: { awards: [{ potAmount: 30, winnings: { A: 30 } }], revealedHands },
    }))

    expect(next.revealedHands).toEqual(revealedHands)
  })
})
