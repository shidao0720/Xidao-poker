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
}

export function CardView({ card, hidden = false, compact = false }: CardViewProps) {
  if (hidden || !card) {
    return (
      <div className={`playing-card card-back${compact ? ' compact' : ''}`} aria-label="未公开的牌">
        <span>XP</span>
      </div>
    )
  }

  const red = card.suit === 'HEARTS' || card.suit === 'DIAMONDS'
  return (
    <div
      className={`playing-card${red ? ' red' : ''}${compact ? ' compact' : ''}`}
      aria-label={`${rankLabels[card.rank]} ${suitLabels[card.suit]}`}
    >
      <span className="card-rank">{rankLabels[card.rank]}</span>
      <span className="card-suit">{suitLabels[card.suit]}</span>
    </div>
  )
}
