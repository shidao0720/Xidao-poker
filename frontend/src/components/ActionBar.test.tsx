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
    revealedHands: [],
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
    expect(screen.getByTestId('action-feedback-burst').getAttribute('data-action')).toBe('CALL')
    expect(screen.getByTestId('action-feedback-burst').querySelectorAll(':scope > i')).toHaveLength(4)
    expect(screen.getByTestId('action-feedback-burst').querySelectorAll('.button-light-burst > i')).toHaveLength(10)
    expect((screen.getByTestId('action-feedback-burst').querySelector(':scope > i') as HTMLElement).style
      .getPropertyValue('--chip-duration')).toBe('900ms')
    expect((screen.getByTestId('action-feedback-burst').querySelectorAll(':scope > i')[1] as HTMLElement).style
      .getPropertyValue('--chip-delay')).toBe('110ms')
  })

  it('keeps the particle burst mounted after the server advances the turn', () => {
    const onAction = vi.fn()
    const snapshot = callableSnapshot()
    const view = render(<ActionBar snapshot={snapshot} playerId="player-a" onAction={onAction} />)

    fireEvent.click(screen.getByRole('button', { name: /跟注/ }))
    expect(screen.getByTestId('action-feedback-burst')).toBeTruthy()

    const nextTurn = callableSnapshot()
    nextTurn.currentActorSeat = 1
    nextTurn.turnId = 4
    view.rerender(<ActionBar snapshot={nextTurn} playerId="player-a" onAction={onAction} />)

    expect(screen.getByTestId('action-feedback-burst')).toBeTruthy()
  })

  it('does not render a dedicated all-in button and maps the maximum raise to ALL_IN', () => {
    const onAction = vi.fn()
    const snapshot = callableSnapshot()
    snapshot.actionOptions.legalActions = ['FOLD', 'CALL', 'RAISE', 'ALL_IN']
    render(<ActionBar snapshot={snapshot} playerId="player-a" onAction={onAction} />)

    expect(screen.queryByRole('button', { name: '全下' })).toBeNull()
    fireEvent.change(screen.getByRole('slider', { name: /加注到/ }), { target: { value: '1000' } })
    fireEvent.click(screen.getByRole('button', { name: /加注/ }))

    expect(onAction).toHaveBeenCalledOnce()
    expect(onAction).toHaveBeenCalledWith('ALL_IN', 0)
  })

  it('keeps a non-maximum raise as a regular RAISE intent', () => {
    const onAction = vi.fn()
    const snapshot = callableSnapshot()
    snapshot.actionOptions.legalActions = ['FOLD', 'CALL', 'RAISE', 'ALL_IN']
    render(<ActionBar snapshot={snapshot} playerId="player-a" onAction={onAction} />)

    fireEvent.change(screen.getByRole('spinbutton', { name: '下注金额' }), { target: { value: '200' } })
    fireEvent.click(screen.getByRole('button', { name: /加注/ }))

    expect(onAction).toHaveBeenCalledOnce()
    expect(onAction).toHaveBeenCalledWith('RAISE', 200)
    expect(screen.getByTestId('action-feedback-burst').getAttribute('data-action')).toBe('RAISE')
    expect(screen.getByTestId('action-feedback-burst').querySelectorAll(':scope > i')).toHaveLength(7)
    expect(screen.getByTestId('action-feedback-burst').querySelectorAll('.button-light-burst > i')).toHaveLength(12)
    expect((screen.getByTestId('action-feedback-burst').querySelector(':scope > i') as HTMLElement).style
      .getPropertyValue('--chip-duration')).toBe('980ms')
    expect((screen.getByTestId('action-feedback-burst').querySelectorAll(':scope > i')[1] as HTMLElement).style
      .getPropertyValue('--chip-delay')).toBe('85ms')
  })

  it('lets the big blind choose a normal raise after the small blind completes', () => {
    const onAction = vi.fn()
    const snapshot = callableSnapshot()
    snapshot.currentActorSeat = 0
    snapshot.players[0]!.streetBet = 20
    snapshot.players[0]!.stack = 980
    snapshot.actionOptions = {
      legalActions: ['CHECK', 'RAISE', 'ALL_IN'],
      toCall: 0,
      callAmount: 0,
      minimumBetTo: null,
      minimumRaiseTo: 40,
      maximumTo: 1_000,
    }
    render(<ActionBar snapshot={snapshot} playerId="player-a" onAction={onAction} />)

    const slider = screen.getByRole('slider', { name: /加注到/ }) as HTMLInputElement
    expect(slider.min).toBe('40')
    expect(slider.max).toBe('1000')
    expect(slider.value).toBe('40')

    fireEvent.change(slider, { target: { value: '200' } })
    fireEvent.click(screen.getByRole('button', { name: /加注/ }))

    expect(onAction).toHaveBeenCalledWith('RAISE', 200)
  })

  it('exposes a short all-in through the amount control without an all-in button', () => {
    const onAction = vi.fn()
    const snapshot = callableSnapshot()
    snapshot.currentBet = 0
    snapshot.actionOptions = {
      legalActions: ['CHECK', 'ALL_IN'],
      toCall: 0,
      callAmount: 0,
      minimumBetTo: null,
      minimumRaiseTo: null,
      maximumTo: 15,
    }
    render(<ActionBar snapshot={snapshot} playerId="player-a" onAction={onAction} />)

    expect(screen.queryByRole('button', { name: '全下' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /下注/ }))

    expect(onAction).toHaveBeenCalledWith('ALL_IN', 0)
  })
})
