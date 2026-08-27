import type { CSSProperties } from 'react'
import type { PlayerSnapshot } from '../types/protocol'
import { CardView } from './CardView'

const statusLabels: Record<PlayerSnapshot['status'], string> = {
  ACTIVE: '在局中',
  FOLDED: '已弃牌',
  ALL_IN: '全下',
  DISCONNECTED: '已离线',
  SPECTATOR: '观战',
  BUSTED: '筹码耗尽',
}

interface PlayerSeatProps {
  player: PlayerSnapshot
  isActor: boolean
  isOwner: boolean
  isSelf: boolean
  position: { x: number; y: number }
}

export function PlayerSeat({ player, isActor, isOwner, isSelf, position }: PlayerSeatProps) {
  const style = { '--seat-x': `${position.x}%`, '--seat-y': `${position.y}%` } as CSSProperties
  return (
    <div
      className={`player-seat${isActor ? ' actor' : ''}${isSelf ? ' self' : ''} status-${player.status.toLowerCase()}`}
      style={style}
    >
      {player.streetBet > 0 && <div className="seat-bet">下注 {player.streetBet.toLocaleString()}</div>}
      <div className="seat-cards" aria-label={`${player.name}的手牌`}>
        {player.holeCards.length > 0 ? (
          player.holeCards.map((card, index) => <CardView card={card} compact key={`${card.rank}-${card.suit}-${index}`} />)
        ) : player.inHand ? (
          <>
            <CardView hidden compact />
            <CardView hidden compact />
          </>
        ) : null}
      </div>
      <div className="seat-panel">
        <div className="avatar" aria-hidden="true">{player.name.slice(0, 1).toUpperCase()}</div>
        <div className="seat-copy">
          <div className="seat-name">
            <span>{player.name}</span>
            {isOwner && <span title="房主" className="owner-mark">♛</span>}
            {isSelf && <span className="self-mark">你</span>}
          </div>
          <strong>{player.stack.toLocaleString()} 筹码</strong>
        </div>
        <span className="status-pill">{isActor ? '行动中' : statusLabels[player.status]}</span>
      </div>
    </div>
  )
}
