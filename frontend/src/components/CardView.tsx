import type { CSSProperties } from 'react'
import type { Card } from '../types/protocol'

const rankLabels: Record<Card['rank'], string> = {
  TWO: '2',
  THREE: '3',
  FOUR: '4',
  FIVE: '5',
  SIX: '6',
  SEVEN: '7',
  EIGHT: '8',
  NINE: '9',
  TEN: '10',
  JACK: 'J',
  QUEEN: 'Q',
  KING: 'K',
  ACE: 'A',
}

const suitLabels: Record<Card['suit'], string> = {
  SPADES: '♠',
  HEARTS: '♥',
  DIAMONDS: '♦',
  CLUBS: '♣',
}

interface CardViewProps {
  card?: Card
  hidden?: boolean
  compact?: boolean
  revealed?: boolean
  dealing?: boolean
  dealDelay?: number
}

export function CardView({
  card,
  hidden = false,
  compact = false,
  revealed = true,
  dealing = false,
  dealDelay = 0,
}: CardViewProps) {
  const style = { '--card-delay': `${dealDelay}ms` } as CSSProperties
  if (hidden || !card) {
    return (
      <div
        className={`playing-card card-back${compact ? ' compact' : ''}${dealing ? ' is-dealing' : ''}`}
        style={style}
        aria-label="未公开的牌"
      >
        <span>XP</span>
      </div>
    )
  }

  const red = card.suit === 'HEARTS' || card.suit === 'DIAMONDS'
  return (
    <div
      className={`playing-card card-flip${revealed ? ' is-revealed' : ''}${compact ? ' compact' : ''}${dealing ? ' is-dealing' : ''}`}
      style={style}
      aria-label={revealed ? `${rankLabels[card.rank]} ${suitLabels[card.suit]}` : '未翻开的手牌'}
    >
      <div className="playing-card-inner">
        <div className="playing-card-face card-back"><span>XP</span></div>
        <div className={`playing-card-face card-front${red ? ' red' : ''}`}>
          <span className="card-rank">{rankLabels[card.rank]}</span>
          <span className="card-suit">{suitLabels[card.suit]}</span>
        </div>
      </div>
    </div>
  )
}
