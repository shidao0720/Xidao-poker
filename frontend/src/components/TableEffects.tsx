import { useLayoutEffect, useState, type CSSProperties } from 'react'
import type { GamePhase, GameSnapshot, HandCategory } from '../types/protocol'
import { CardView } from './CardView'
import { cosmeticClass } from '../utils/cosmetics'

function particleStyle(index: number, count: number, prefix: 'allin' | 'result') {
  const angle = (index / count) * Math.PI * 2 + (index % 3) * 0.11
  const distance = prefix === 'allin' ? 170 + (index % 6) * 34 : 210 + (index % 7) * 31
  return {
    [`--${prefix}-x`]: `${Math.cos(angle) * distance}px`,
    [`--${prefix}-y`]: `${Math.sin(angle) * distance}px`,
    [`--${prefix}-delay`]: `${(index % 8) * 70}ms`,
    [`--${prefix}-scale`]: `${0.65 + (index % 4) * 0.18}`,
  } as CSSProperties
}

export function DeckStack({ handId, phase }: { handId: number; phase: GamePhase }) {
  const shuffling = phase === 'DEALING'
  return (
    <div className={`table-deck${shuffling ? ' is-shuffling' : ''}`} key={handId} aria-hidden="true">
      <i className="deck-card deck-under" />
      <i className="deck-card deck-middle" />
      <i className="deck-card deck-top" />
      <b>XP</b>
    </div>
  )
}

export function AllInBroadcast({
  visible,
  amount,
  onDismiss,
  effectKey,
}: {
  visible: boolean
  amount: number
  onDismiss: () => void
  effectKey?: string | null | undefined
}) {
  return (
    <section
      className={`allin-broadcast${visible ? ' is-visible' : ''} ${cosmeticClass('button-effect', effectKey)}`}
      aria-hidden={!visible}
      onClick={visible ? onDismiss : undefined}
    >
      <div className="allin-lines" aria-hidden="true" />
      <div className="allin-particles" aria-hidden="true">
        {Array.from({ length: 24 }, (_, index) => (
          <i key={index} style={particleStyle(index, 24, 'allin')} />
        ))}
      </div>
      <div className="allin-seal" aria-hidden="true"><i /><i /><i /></div>
      <h2>ALL IN</h2>
      <strong>{amount.toLocaleString()} CHIPS</strong>
    </section>
  )
}

const handCategoryLabels: Record<HandCategory, string> = {
  HIGH_CARD: '高牌',
  ONE_PAIR: '一对',
  TWO_PAIR: '两对',
  THREE_OF_A_KIND: '三条',
  STRAIGHT: '顺子',
  FLUSH: '同花',
  FULL_HOUSE: '葫芦',
  FOUR_OF_A_KIND: '四条',
  STRAIGHT_FLUSH: '同花顺',
}

interface SettlementOverlayProps {
  snapshot: GameSnapshot
  playerId: string
  visible: boolean
  onClose: () => void
}

export function SettlementOverlay({ snapshot, playerId, visible, onClose }: SettlementOverlayProps) {
  const winnings = new Map<string, number>()
  snapshot.awards.forEach((award) => {
    Object.entries(award.winnings).forEach(([winnerId, amount]) => {
      winnings.set(winnerId, (winnings.get(winnerId) ?? 0) + amount)
    })
  })
  const rankedWinners = [...winnings.entries()].sort((left, right) => right[1] - left[1])
  const primaryWinner = snapshot.players.find((player) => player.id === rankedWinners[0]?.[0])
  const primaryWinnerHand = (snapshot.revealedHands ?? [])
    .find((hand) => hand.playerId === primaryWinner?.id)
  const primaryWinnerCards = primaryWinnerHand?.holeCards ?? primaryWinner?.holeCards ?? []
  const evaluatedCards = primaryWinnerHand?.bestCards ?? []
  const combinationCards = evaluatedCards.length === 5 ? evaluatedCards : primaryWinnerCards
  const combinationLabel = evaluatedCards.length === 5 ? '最佳五张组合' : '公开手牌'
  const holeCardKeys = new Set(primaryWinnerCards.map((card) => `${card.rank}-${card.suit}`))
  const winnerHandLabel = primaryWinnerHand?.category
    ? handCategoryLabels[primaryWinnerHand.category]
    : '未摊牌获胜'
  const winnerNames = rankedWinners
    .map(([winnerId]) => snapshot.players.find((player) => player.id === winnerId)?.name)
    .filter((name): name is string => Boolean(name))
  const self = snapshot.players.find((player) => player.id === playerId)
  const personalWinnings = winnings.get(playerId) ?? 0
  const personalDelta = personalWinnings - (self?.totalContribution ?? 0)
  const outcome = personalDelta > 0 ? 'win' : personalDelta < 0 ? 'lose' : 'neutral'
  const title = outcome === 'win' ? 'Victory' : outcome === 'lose' ? 'Defeat' : 'FATE UNBOUND'
  const deltaLabel = `${personalDelta > 0 ? '+' : ''}${personalDelta.toLocaleString()}`

  return (
    <section
      className={`settlement-overlay${visible ? ' is-visible' : ''} ${cosmeticClass('victory-effect', primaryWinner?.cosmetics?.victoryEffect)}`}
      data-outcome={outcome}
      aria-hidden={!visible}
    >
      <div className="result-atmosphere" aria-hidden="true">
        <div className="fate-result-sigil"><i /><i /><i /></div>
        {Array.from({ length: 28 }, (_, index) => (
          <i key={index} style={particleStyle(index, 28, 'result')} />
        ))}
      </div>
      <div className="result-panel">
        <button className="result-close" onClick={onClose} aria-label="关闭结算">×</button>
        <span className="result-code">SHOWDOWN / GRAIL VERDICT</span>
        <h2>{title}</h2>
        <div className="winner-summary">
          <div className="winner-identity">
            <span className="winner-avatar">{primaryWinner?.name.slice(0, 1).toUpperCase() ?? '—'}</span>
            <div className="winner-copy">
              <small>本轮获胜者</small>
              <strong>{winnerNames.join(' · ') || '无人获奖'}</strong>
            </div>
          </div>
          <div className="winner-combination" aria-label={`${primaryWinner?.name ?? '赢家'}的${combinationLabel}`}>
            <div className="winner-combination-heading">
              <small>{combinationLabel}</small>
              <strong className="winner-hand-category">{winnerHandLabel}</strong>
            </div>
            <div className="winner-combination-cards">
              {combinationCards.map((card, index) => {
                const fromHole = holeCardKeys.has(`${card.rank}-${card.suit}`)
                return (
                  <span className={`winner-combination-card${fromHole ? ' is-hole' : ' is-board'}`} key={`${card.rank}-${card.suit}-${index}`}>
                    <CardView card={card} compact />
                    <small>{fromHole ? '手牌' : '公共牌'}</small>
                  </span>
                )
              })}
            </div>
          </div>
        </div>
        <div className="personal-result">
          <span><small>本轮净变化</small><strong>{self?.name}</strong></span>
          <b>{deltaLabel}</b>
          <em>获得 {personalWinnings.toLocaleString()} · 投入 {(self?.totalContribution ?? 0).toLocaleString()}</em>
        </div>
      </div>
    </section>
  )
}

export function settlementTransferDuration(amount: number) {
  return Math.round(Math.max(500, Math.min(2_000, 500 + Math.sqrt(Math.max(0, amount)) * 45)))
}

export const SETTLEMENT_VERDICT_MAX_MS = 5_000

export function settlementParticleTiming(index: number, count: number, duration: number) {
  const launchWindow = duration * 0.52
  const delay = count <= 1 ? 0 : (index / (count - 1)) * launchWindow
  return {
    delay,
    travelDuration: duration - launchWindow,
  }
}

interface TransferPath {
  winnerId: string
  amount: number
  startX: number
  startY: number
  endX: number
  endY: number
}

interface SettlementTransferProps {
  active: boolean
  handId: number
  duration: number
  winnings: Record<string, number>
}

export function SettlementTransfer({ active, handId, duration, winnings }: SettlementTransferProps) {
  const [paths, setPaths] = useState<TransferPath[]>([])

  useLayoutEffect(() => {
    if (!active) {
      setPaths([])
      return
    }

    const potAnchor = document.querySelector<HTMLElement>('[data-pot-anchor]')
    const stackAnchors = [...document.querySelectorAll<HTMLElement>('[data-player-stack-anchor]')]
    if (!potAnchor) return

    const measure = () => {
      const potBounds = potAnchor.getBoundingClientRect()
      const startX = potBounds.left + potBounds.width / 2
      const startY = potBounds.top + potBounds.height / 2
      setPaths(Object.entries(winnings).flatMap(([winnerId, amount]) => {
        const target = stackAnchors.find((anchor) => anchor.dataset.playerStackAnchor === winnerId)
        if (!target || amount <= 0) return []
        const bounds = target.getBoundingClientRect()
        return [{
          winnerId,
          amount,
          startX,
          startY,
          endX: bounds.left + bounds.width / 2,
          endY: bounds.top + bounds.height / 2,
        }]
      }))
    }

    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [active, handId, winnings])

  if (!active) return null

  return (
    <div className="settlement-transfer-layer" data-testid="settlement-chip-transfer" aria-hidden="true">
      {paths.flatMap((path) => {
        const count = Math.min(12, Math.max(5, Math.ceil(Math.log10(path.amount + 1) * 4)))
        const deltaX = path.endX - path.startX
        const deltaY = path.endY - path.startY
        const chips = Array.from({ length: count }, (_, index) => {
          const timing = settlementParticleTiming(index, count, duration)
          return (
            <i
              className="settlement-flying-chip"
              key={`${path.winnerId}-${index}`}
              style={{
                '--settle-start-x': `${path.startX}px`,
                '--settle-start-y': `${path.startY}px`,
                '--settle-end-x': `${deltaX + ((index % 3) - 1) * 3}px`,
                '--settle-end-y': `${deltaY + ((index % 2) - 0.5) * 3}px`,
                '--settle-delay': `${timing.delay}ms`,
                '--settle-duration': `${Math.max(240, timing.travelDuration)}ms`,
                '--settle-spin': `${360 + index * 73}deg`,
              } as CSSProperties}
            />
          )
        })
        chips.push(
          <b
            className="settlement-stack-impact"
            key={`${path.winnerId}-impact`}
            style={{
              '--settle-target-x': `${path.endX}px`,
              '--settle-target-y': `${path.endY}px`,
              '--settle-impact-delay': `${duration * 0.9}ms`,
              '--settle-impact-duration': `${duration * 0.09}ms`,
            } as CSSProperties}
          />,
        )
        return chips
      })}
    </div>
  )
}
