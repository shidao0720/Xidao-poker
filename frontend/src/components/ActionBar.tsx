import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import type { ActionType, GameSnapshot } from '../types/protocol'
import { cosmeticClass } from '../utils/cosmetics'

interface ActionBarProps {
  snapshot: GameSnapshot
  playerId: string
  onAction: (action: ActionType, amount?: number) => void
  effectKey?: string | null | undefined
}

const actionLabels: Record<ActionType, string> = {
  FOLD: '弃牌',
  CHECK: '过牌',
  CALL: '跟注',
  BET: '下注',
  RAISE: '加注',
  ALL_IN: '全下',
}

export function ActionBar({ snapshot, playerId, onAction, effectKey }: ActionBarProps) {
  const options = snapshot.actionOptions
  const legal = options.legalActions
  const standardAggressiveAction = legal.includes('RAISE') ? 'RAISE' : legal.includes('BET') ? 'BET' : null
  const allInOnlyAggressiveAction = standardAggressiveAction === null
    && legal.includes('ALL_IN')
    && options.maximumTo > snapshot.currentBet
    ? snapshot.currentBet === 0 ? 'BET' : 'RAISE'
    : null
  const aggressiveAction = standardAggressiveAction ?? allInOnlyAggressiveAction
  const minimum = standardAggressiveAction === 'RAISE'
    ? options.minimumRaiseTo
    : standardAggressiveAction === 'BET'
      ? options.minimumBetTo
      : allInOnlyAggressiveAction
        ? options.maximumTo
        : null
  const initialAmount = Math.min(Math.max(minimum ?? 1, 1), options.maximumTo)
  const [amount, setAmount] = useState(initialAmount)
  const [feedback, setFeedback] = useState<{
    target: ActionType
    id: number
    x: number
    y: number
    targetX: number
    targetY: number
  } | null>(null)

  useEffect(() => setAmount(initialAmount), [initialAmount, snapshot.turnId])
  useEffect(() => {
    if (!feedback) return
    const timer = window.setTimeout(() => setFeedback(null), 1_800)
    return () => window.clearTimeout(timer)
  }, [feedback])

  const currentPlayer = snapshot.players.find((player) => player.id === playerId)
  const isCurrentActor = currentPlayer?.seat === snapshot.currentActorSeat
  const displayActions = useMemo(
    () => legal.filter((action) => action !== 'BET' && action !== 'RAISE' && action !== 'ALL_IN'),
    [legal],
  )
  const chargeRatio = minimum === null || options.maximumTo <= minimum
    ? 0
    : Math.min(1, Math.max(0, (amount - minimum) / (options.maximumTo - minimum)))
  const fractureEarly = Math.min(1, chargeRatio / 0.42)
  const fractureMid = Math.min(1, Math.max(0, (chargeRatio - 0.24) / 0.48))
  const fractureLate = Math.min(1, Math.max(0, (chargeRatio - 0.56) / 0.44))
  const shardGlow = Math.min(1, Math.max(0, (chargeRatio - 0.8) / 0.2))
  const dockStyle = {
    '--grail-gem-deep': `hsl(219 78% ${7 + chargeRatio * 5}%)`,
    '--grail-gem-mid': `hsl(207 80% ${9 + chargeRatio * 8}%)`,
    '--grail-gem-cyan': `hsl(190 80% ${11 + chargeRatio * 11}%)`,
    '--grail-facet-alpha': 0.03 + chargeRatio * 0.15,
    '--grail-refraction-alpha': 0.02 + chargeRatio * 0.08,
    '--grail-crack-early': fractureEarly * 0.48,
    '--grail-crack-mid': fractureMid * 0.52,
    '--grail-crack-late': fractureLate * 0.58,
    '--grail-shard-glow': shardGlow * 0.82,
    '--grail-charge-glow': 0.04 + chargeRatio * 0.08,
  } as CSSProperties

  function submit(
    action: ActionType,
    submittedAmount: number,
    origin: HTMLButtonElement,
    feedbackTarget = action,
  ) {
    const bounds = origin.getBoundingClientRect()
    const potBounds = document.querySelector<HTMLElement>('[data-pot-anchor]')?.getBoundingClientRect()
    const originX = Math.max(16, Math.min(window.innerWidth - 16, bounds.left + bounds.width / 2))
    const originY = Math.max(16, Math.min(window.innerHeight - 16, bounds.top + bounds.height / 2))
    setFeedback((current) => ({
      target: feedbackTarget,
      id: (current?.id ?? 0) + 1,
      x: originX,
      y: originY,
      targetX: potBounds && potBounds.width > 0
        ? potBounds.left + potBounds.width / 2
        : window.innerWidth / 2,
      targetY: potBounds && potBounds.height > 0
        ? potBounds.top + potBounds.height / 2
        : window.innerHeight * 0.38,
    }))
    onAction(action, submittedAmount)
  }

  const feedbackBurst = feedback && (
    <ActionPulse
      action={feedback.target}
      key={feedback.id}
      x={feedback.x}
      y={feedback.y}
      targetX={feedback.targetX}
      targetY={feedback.targetY}
      effectKey={effectKey}
    />
  )

  if (!isCurrentActor || legal.length === 0) return feedbackBurst

  return (
    <>
      <section
        className={`action-dock${amount === options.maximumTo ? ' is-max' : ''} ${cosmeticClass('button-effect', effectKey)}`}
        style={dockStyle}
        aria-label="玩家操作"
      >
        {aggressiveAction && minimum !== null && options.maximumTo >= minimum && (
          <div className="raise-control">
            <label htmlFor="raise-amount-slider">{actionLabels[aggressiveAction]}到</label>
            <input
              id="raise-amount-slider"
              name="raiseAmountSlider"
              type="range"
              min={minimum}
              max={options.maximumTo}
              value={amount}
              onChange={(event) => setAmount(Number(event.target.value))}
            />
            <input
              id="raise-amount-input"
              name="raiseAmount"
              aria-label="下注金额"
              className="amount-input"
              type="number"
              min={minimum}
              max={options.maximumTo}
              value={amount}
              onChange={(event) => {
                const value = Number(event.target.value)
                setAmount(Math.min(Math.max(value, minimum), options.maximumTo))
              }}
            />
          </div>
        )}
        <div className="action-buttons">
          {displayActions.map((action) => (
            <button
              className={`action-button action-${action.toLowerCase()}`}
              key={action}
              onClick={(event) => submit(action, 0, event.currentTarget)}
            >
              {actionLabels[action]}
              {action === 'CALL' && options.callAmount > 0 && <small>{options.callAmount.toLocaleString()}</small>}
            </button>
          ))}
          {aggressiveAction && minimum !== null && options.maximumTo >= minimum && (
            <button
              className="action-button action-aggressive"
              onClick={(event) => {
                if (legal.includes('ALL_IN') && amount === options.maximumTo) {
                  submit('ALL_IN', 0, event.currentTarget, aggressiveAction)
                  return
                }
                submit(aggressiveAction, amount, event.currentTarget)
              }}
            >
              {actionLabels[aggressiveAction]}
              <small>{amount.toLocaleString()}</small>
            </button>
          )}
        </div>
      </section>
      {feedbackBurst}
    </>
  )
}

function ActionPulse({
  action,
  x,
  y,
  targetX,
  targetY,
  effectKey,
}: {
  action: ActionType
  x: number
  y: number
  targetX: number
  targetY: number
  effectKey?: string | null | undefined
}) {
  const wagerFlight = action === 'CALL' || action === 'BET' || action === 'RAISE'
  const particleCount = wagerFlight ? action === 'CALL' ? 4 : 7 : 12
  const deltaX = targetX - x
  const deltaY = targetY - y
  const chipDuration = action === 'CALL' ? 900 : 980
  const chipInterval = action === 'CALL' ? 110 : 85
  const rayCount = action === 'FOLD' ? 7 : action === 'CHECK' ? 8 : action === 'CALL' ? 10 : 12
  const style = {
    '--feedback-x': `${x}px`,
    '--feedback-y': `${y}px`,
    '--pot-delta-x': `${deltaX}px`,
    '--pot-delta-y': `${deltaY}px`,
    '--pot-impact-delay': `${chipDuration + (particleCount - 1) * chipInterval - 110}ms`,
  } as CSSProperties
  return (
    <span
      className={`action-feedback-wave${wagerFlight ? ' is-wager-flight' : ''} ${cosmeticClass('button-effect', effectKey)}`}
      data-action={action}
      data-testid="action-feedback-burst"
      style={style}
      aria-hidden="true"
    >
      <b className="feedback-ring" />
      <b className="feedback-core" />
      <b className="feedback-slash" />
      <span className="button-light-burst">
        {Array.from({ length: rayCount }, (_, index) => (
          <i
            key={index}
            style={{
              '--ray-angle': `${(360 / rayCount) * index + (index % 2) * 5}deg`,
              '--ray-length': `${26 + (index % 4) * 9}px`,
              '--ray-delay': `${(index % 3) * 18}ms`,
            } as CSSProperties}
          />
        ))}
      </span>
      {wagerFlight && <b className="pot-impact" />}
      {Array.from({ length: particleCount }, (_, index) => {
        return (
          <i
            key={index}
            style={wagerFlight ? {
              '--chip-end-x': `${deltaX + ((index % 3) - 1) * 4}px`,
              '--chip-end-y': `${deltaY + ((index % 2) - 0.5) * 4}px`,
              '--chip-delay': `${index * chipInterval}ms`,
              '--chip-duration': `${chipDuration}ms`,
              '--chip-spin': `${220 + index * 47}deg`,
            } as CSSProperties : undefined}
          />
        )
      })}
    </span>
  )
}
