import { useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import type { PlayerSnapshot } from '../types/protocol'
import { CardView } from './CardView'
import { AvatarView } from './AvatarView'
import { cosmeticClass, cosmeticLabel } from '../utils/cosmetics'

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
  displayStack?: number
  stackAnimating?: boolean
}

export function PlayerSeat({
  player,
  isActor,
  isOwner,
  isSelf,
  position,
  displayStack = player.stack,
  stackAnimating = false,
}: PlayerSeatProps) {
  const [cardsRevealed, setCardsRevealed] = useState(!isSelf)
  const pointerStartY = useRef<number | null>(null)
  const style = {
    '--seat-x': `${position.x}%`,
    '--seat-y': `${position.y}%`,
    '--deal-from-x': `${(58 - position.x) * 0.78}vw`,
    '--deal-from-y': `${(32 - position.y) * 0.55}vh`,
  } as CSSProperties
  const canReveal = isSelf && player.holeCards.length > 0 && !cardsRevealed
  const avatarFrame = cosmeticClass('cosmetic-avatar-frame', player.cosmetics?.avatarFrame)
  const title = cosmeticLabel(player.cosmetics?.title)
  const cardBack = player.cosmetics?.cardBack

  function revealCards() {
    if (canReveal) setCardsRevealed(true)
  }

  function finishSwipe(event: PointerEvent<HTMLDivElement>) {
    if (pointerStartY.current !== null && pointerStartY.current - event.clientY > 22) revealCards()
    pointerStartY.current = null
  }

  return (
    <div
      className={`player-seat${isActor ? ' actor' : ''}${isSelf ? ' self' : ''} status-${player.status.toLowerCase()}`}
      style={style}
    >
      {player.streetBet > 0 && <div className="seat-bet">下注 {player.streetBet.toLocaleString()}</div>}
      <div
        className={`seat-cards${canReveal ? ' is-revealable' : ''}`}
        aria-label={canReveal ? '点击或向上滑动翻开手牌' : `${player.name}的手牌`}
        role={canReveal ? 'button' : undefined}
        tabIndex={canReveal ? 0 : undefined}
        onClick={revealCards}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') revealCards()
        }}
        onPointerDown={(event) => { pointerStartY.current = event.clientY }}
        onPointerUp={finishSwipe}
        onPointerCancel={() => { pointerStartY.current = null }}
      >
        {canReveal && (
          <span className="card-reveal-hint" aria-hidden="true">
            <i>←</i>
            点击翻牌
          </span>
        )}
        {player.holeCards.length > 0 ? (
          player.holeCards.map((card, index) => (
            <span
              className="seat-card-flight"
              key={`${card.rank}-${card.suit}-${index}`}
              style={{ '--card-delay': `${index * 130}ms` } as CSSProperties}
            >
              <CardView card={card} compact revealed={!isSelf || cardsRevealed} backKey={cardBack} />
            </span>
          ))
        ) : player.inHand ? (
          <>
            <span className="seat-card-flight" style={{ '--card-delay': '0ms' } as CSSProperties}><CardView hidden compact backKey={cardBack} /></span>
            <span className="seat-card-flight" style={{ '--card-delay': '130ms' } as CSSProperties}><CardView hidden compact backKey={cardBack} /></span>
          </>
        ) : null}
      </div>
      <div className="seat-panel">
        <span className={`seat-avatar-cosmetic${avatarFrame ? ` ${avatarFrame}` : ''}`}>
          <AvatarView className="avatar" avatarKey={player.avatarKey} name={player.name} />
        </span>
        <div className="seat-copy">
          <div className="seat-name">
            <span>{player.name}</span>
            {isOwner && <span title="房主" className="owner-mark">♛</span>}
            {isSelf && <span className="self-mark">你</span>}
          </div>
          <strong
            className={stackAnimating ? 'is-settling' : undefined}
            data-player-stack-anchor={player.id}
          >
            {displayStack.toLocaleString()} 筹码
          </strong>
          {title && <small className={`seat-cosmetic-title ${cosmeticClass('cosmetic-title', player.cosmetics?.title)}`}>{title}</small>}
        </div>
        <span className="status-pill">{isActor ? '行动中' : statusLabels[player.status]}</span>
      </div>
    </div>
  )
}
