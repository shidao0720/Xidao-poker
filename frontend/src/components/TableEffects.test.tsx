// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { GameSnapshot } from '../types/protocol'
import {
  AllInBroadcast,
  SETTLEMENT_VERDICT_MAX_MS,
  SettlementOverlay,
  settlementParticleTiming,
  settlementTransferDuration,
} from './TableEffects'

afterEach(cleanup)

function settledSnapshot(): GameSnapshot {
  return {
    sessionId: 'room-1',
    ownerId: 'winner',
    handId: 2,
    phase: 'ROUND_END',
    buttonSeat: 0,
    smallBlindSeat: 0,
    bigBlindSeat: 1,
    currentActorSeat: null,
    turnId: 8,
    currentBet: 0,
    minimumRaise: 10,
    pot: 30,
    pots: [],
    communityCards: [],
    players: [
      {
        id: 'winner', name: 'Alice', seat: 0, stack: 1_015, streetBet: 0,
        totalContribution: 15, status: 'ACTIVE', inHand: true, canAct: false,
        ready: true, holeCards: [{ rank: 'ACE', suit: 'SPADES' }, { rank: 'ACE', suit: 'HEARTS' }],
      },
      {
        id: 'loser', name: 'Bob', seat: 1, stack: 985, streetBet: 0,
        totalContribution: 15, status: 'ACTIVE', inHand: true, canAct: false,
        ready: true, holeCards: [],
      },
    ],
    actionOptions: {
      legalActions: [], toCall: 0, callAmount: 0,
      minimumBetTo: null, minimumRaiseTo: null, maximumTo: 0,
    },
    awards: [{ potAmount: 30, winnings: { winner: 30 } }],
    revealedHands: [{
      playerId: 'winner',
      showdown: true,
      category: 'ONE_PAIR',
      holeCards: [{ rank: 'ACE', suit: 'SPADES' }, { rank: 'ACE', suit: 'HEARTS' }],
      bestCards: [
        { rank: 'ACE', suit: 'SPADES' },
        { rank: 'ACE', suit: 'HEARTS' },
        { rank: 'KING', suit: 'SPADES' },
        { rank: 'QUEEN', suit: 'SPADES' },
        { rank: 'TEN', suit: 'SPADES' },
      ],
    }],
    lastSequence: 20,
  }
}

describe('SettlementOverlay', () => {
  it('uses the Fate verdict title and renders the visible particle field', () => {
    const view = render(
      <SettlementOverlay snapshot={settledSnapshot()} playerId="winner" visible onClose={vi.fn()} />,
    )

    expect(screen.getByRole('heading', { name: 'FATE CLAIMED' })).toBeTruthy()
    expect(screen.getByText('+15')).toBeTruthy()
    expect(screen.getByText('一对')).toBeTruthy()
    expect(view.container.querySelectorAll('.winner-combination-card')).toHaveLength(5)
    expect(screen.getAllByText('手牌')).toHaveLength(2)
    expect(screen.getAllByText('公共牌')).toHaveLength(3)
    expect(view.container.querySelectorAll('.result-atmosphere > i')).toHaveLength(28)
    expect(view.container.querySelector('.settlement-overlay.is-visible')).toBeTruthy()
  })

  it('keeps rendering a settlement received from an older backend without revealedHands', () => {
    const legacySnapshot = settledSnapshot()
    delete (legacySnapshot as Partial<GameSnapshot>).revealedHands

    render(
      <SettlementOverlay snapshot={legacySnapshot} playerId="winner" visible onClose={vi.fn()} />,
    )

    expect(screen.getByRole('heading', { name: 'FATE CLAIMED' })).toBeTruthy()
    expect(screen.getByText('未摊牌获胜')).toBeTruthy()
  })

  it('lets any click dismiss the all-in broadcast while keeping automatic control external', () => {
    const onDismiss = vi.fn()
    const view = render(<AllInBroadcast visible amount={1_000} onDismiss={onDismiss} />)

    fireEvent.click(view.container.querySelector('.allin-broadcast')!)

    expect(onDismiss).toHaveBeenCalledOnce()
  })

  it('scales transfer time by pot size and clamps it between half and two seconds', () => {
    expect(settlementTransferDuration(0)).toBe(500)
    expect(settlementTransferDuration(100)).toBeGreaterThan(500)
    expect(settlementTransferDuration(100_000)).toBe(2_000)
  })

  it('keeps the verdict available for at most five seconds and launches chips as a stream', () => {
    expect(SETTLEMENT_VERDICT_MAX_MS).toBe(5_000)
    expect(settlementParticleTiming(0, 6, 2_000)).toEqual({ delay: 0, travelDuration: 960 })
    expect(settlementParticleTiming(5, 6, 2_000)).toEqual({ delay: 1_040, travelDuration: 960 })
  })
})
