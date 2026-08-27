// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { GameSnapshot } from '../types/protocol'
import { ActionBar } from './ActionBar'

afterEach(cleanup)

function callableSnapshot(): GameSnapshot {
  return {
    sessionId: 'room-1',
    ownerId: 'player-a',
    handId: 1,
    phase: 'PREFLOP',
    buttonSeat: 0,
    smallBlindSeat: 1,
    bigBlindSeat: 2,
    currentActorSeat: 0,
    turnId: 3,
    currentBet: 20,
    minimumRaise: 20,
    pot: 30,
    pots: [],
    communityCards: [],
    players: [{
      id: 'player-a',
      name: 'Alice',
      seat: 0,
      stack: 1_000,
      streetBet: 0,
      totalContribution: 0,
      status: 'ACTIVE',
      inHand: true,
      canAct: true,
      ready: true,
      holeCards: [],
    }],
    actionOptions: {
      legalActions: ['FOLD', 'CALL', 'RAISE'],
      toCall: 20,
      callAmount: 20,
      minimumBetTo: null,
      minimumRaiseTo: 40,
      maximumTo: 1_000,
    },
    awards: [],
    lastSequence: 10,
  }
}

describe('ActionBar', () => {
  it('shows the call amount but sends CALL as an amount-free intent', () => {
    const onAction = vi.fn()
    render(<ActionBar snapshot={callableSnapshot()} playerId="player-a" onAction={onAction} />)

    const callButton = screen.getByRole('button', { name: /跟注/ })
    expect(callButton.textContent).toContain('20')

    fireEvent.click(callButton)

    expect(onAction).toHaveBeenCalledOnce()
    expect(onAction).toHaveBeenCalledWith('CALL', 0)
  })
})
