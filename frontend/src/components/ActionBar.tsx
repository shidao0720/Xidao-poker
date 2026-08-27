import { useEffect, useMemo, useState } from 'react'
import type { ActionType, GameSnapshot } from '../types/protocol'

interface ActionBarProps {
  snapshot: GameSnapshot
  playerId: string
  onAction: (action: ActionType, amount?: number) => void
}

const actionLabels: Record<ActionType, string> = {
  FOLD: '弃牌',
  CHECK: '过牌',
  CALL: '跟注',
  BET: '下注',
  RAISE: '加注',
  ALL_IN: '全下',
}

export function ActionBar({ snapshot, playerId, onAction }: ActionBarProps) {
  const options = snapshot.actionOptions
  const legal = options.legalActions
  const aggressiveAction = legal.includes('RAISE') ? 'RAISE' : legal.includes('BET') ? 'BET' : null
  const minimum = aggressiveAction === 'RAISE' ? options.minimumRaiseTo : options.minimumBetTo
  const initialAmount = Math.min(Math.max(minimum ?? 1, 1), options.maximumTo)
  const [amount, setAmount] = useState(initialAmount)

  useEffect(() => setAmount(initialAmount), [initialAmount, snapshot.turnId])

  const currentPlayer = snapshot.players.find((player) => player.id === playerId)
  const isCurrentActor = currentPlayer?.seat === snapshot.currentActorSeat
  const displayActions = useMemo(
    () => legal.filter((action) => action !== 'BET' && action !== 'RAISE'),
    [legal],
  )

  if (!isCurrentActor || legal.length === 0) return null

  return (
    <section className="action-dock" aria-label="玩家操作">
      <div className="action-prompt">
        <span>轮到你行动</span>
        <strong>{options.toCall > 0 ? `需跟 ${options.toCall.toLocaleString()}` : '可以过牌'}</strong>
      </div>
      {aggressiveAction && minimum !== null && options.maximumTo >= minimum && (
        <div className="raise-control">
          <label htmlFor="raise-amount">{actionLabels[aggressiveAction]}到</label>
          <input
            id="raise-amount"
            type="range"
            min={minimum}
            max={options.maximumTo}
            value={amount}
            onChange={(event) => setAmount(Number(event.target.value))}
          />
          <input
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
            onClick={() => onAction(action, action === 'CALL' ? options.callAmount : 0)}
          >
            {actionLabels[action]}
            {action === 'CALL' && options.callAmount > 0 && <small>{options.callAmount.toLocaleString()}</small>}
          </button>
        ))}
        {aggressiveAction && minimum !== null && options.maximumTo >= minimum && (
          <button className="action-button action-aggressive" onClick={() => onAction(aggressiveAction, amount)}>
            {actionLabels[aggressiveAction]}
            <small>{amount.toLocaleString()}</small>
          </button>
        )}
      </div>
    </section>
  )
}
