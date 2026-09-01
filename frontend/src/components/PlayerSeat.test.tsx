// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { PlayerSnapshot } from '../types/protocol'
import { PlayerSeat } from './PlayerSeat'

afterEach(cleanup)

const player: PlayerSnapshot = {
  id: 'player-a',
  name: 'Alice',
  seat: 0,
  stack: 995,
  streetBet: 5,
  totalContribution: 5,
  status: 'ACTIVE',
  connectionStatus: 'CONNECTED',
  seatStatus: 'SEATED',
  handStatus: 'ACTIVE',
  inHand: true,
  canAct: true,
  ready: true,
  holeCards: [
    { rank: 'ACE', suit: 'SPADES' },
    { rank: 'KING', suit: 'HEARTS' },
  ],
}

describe('PlayerSeat', () => {
  it('shows the click-to-reveal hint until the local player flips their cards', () => {
    render(
      <PlayerSeat
        player={player}
        isActor
        isOwner
        isSelf
        position={{ x: 50, y: 86 }}
      />,
    )

    expect(screen.getByText('点击翻牌')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '点击或向上滑动翻开手牌' }))
    expect(screen.queryByText('点击翻牌')).toBeNull()
  })

  it('renders only the cosmetic loadout supplied by the server snapshot', () => {
    const decoratedPlayer: PlayerSnapshot = {
      ...player,
      cosmetics: {
        avatarFrame: 'observer-frame',
        cardBack: 'red-lance',
        title: 'chosen-observer',
      },
    }

    const view = render(
      <PlayerSeat
        player={decoratedPlayer}
        isActor
        isOwner={false}
        isSelf
        position={{ x: 50, y: 86 }}
      />,
    )

    expect(view.container.querySelector('.cosmetic-avatar-frame-observer-frame')).toBeTruthy()
    expect(view.container.querySelectorAll('.cosmetic-card-back-red-lance')).toHaveLength(2)
    expect(screen.getByText('被选中的观测者')).toBeTruthy()
  })
})
