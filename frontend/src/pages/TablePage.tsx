import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ActionBar } from '../components/ActionBar'
import { CardView } from '../components/CardView'
import { ConnectionBadge } from '../components/ConnectionBadge'
import { PlayerSeat } from '../components/PlayerSeat'
import {
  AllInBroadcast,
  DeckStack,
  SettlementOverlay,
  SettlementTransfer,
  SETTLEMENT_VERDICT_MAX_MS,
  settlementTransferDuration,
} from '../components/TableEffects'
import { useGameStore } from '../store/gameStore'
import { useAccountStore } from '../store/accountStore'
import type { ActionType, GamePhase, GameSnapshot, PlayerStatus } from '../types/protocol'
import { getPlayerId, getPlayerName } from '../utils/identity'
import { positionPlayersForViewer } from '../utils/seatLayout'
import { rememberPendingTableResult, saveTableResult } from '../utils/tableExit'
import { PokerSocket } from '../ws/PokerSocket'

const phaseLabels: Record<GamePhase, string> = {
  WAITING: '等待玩家',
  READY: '全部就绪',
  DEALING: '正在发牌',
  PREFLOP: '翻牌前',
  FLOP: '翻牌圈',
  TURN: '转牌圈',
  RIVER: '河牌圈',
  SHOWDOWN: '摊牌',
  SETTLEMENT: '结算',
  ROUND_END: '本手结束',
}

interface SettlementAnimation {
  handId: number
  progress: number
  duration: number
  winnings: Record<string, number>
  active: boolean
}

export function TablePage() {
  const { roomId } = useParams()
  const navigate = useNavigate()
  const socketRef = useRef<PokerSocket | null>(null)
  const accountProfile = useAccountStore((state) => state.profile)
  const guestPlayerId = useMemo(getPlayerId, [])
  const guestPlayerName = useMemo(getPlayerName, [])
  const playerId = accountProfile?.gameId ?? guestPlayerId
  const playerName = accountProfile?.gameId ?? guestPlayerName
  const playerAvatarKey = accountProfile?.avatarKey ?? 'default'
  const { snapshot, connection, lastError, notice, setError, setNotice, reset } = useGameStore()
  const [allInVisible, setAllInVisible] = useState(false)
  const [dismissedSettlementHand, setDismissedSettlementHand] = useState<number | null>(null)
  const [settlementAnimation, setSettlementAnimation] = useState<SettlementAnimation | null>(null)
  const previousSelfStatus = useRef<PlayerStatus | undefined>(undefined)
  const previousHandId = useRef<number | undefined>(undefined)
  const settlementFrame = useRef<number | null>(null)
  const settlementStartedHand = useRef<number | null>(null)
  const initialStack = useRef<number | null>(null)

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0 })
  }, [])

  useEffect(() => {
    if (!roomId || !playerName) {
      navigate('/', { replace: true })
      return
    }
    const socket = new PokerSocket()
    socketRef.current = socket
    // 延迟一个任务周期，避免 React StrictMode 的开发期 setup/cleanup 探测创建幽灵连接。
    const connectTimer = window.setTimeout(() => {
      socket.connect({ roomId, playerId, playerName, avatarKey: playerAvatarKey })
    }, 0)
    return () => {
      window.clearTimeout(connectTimer)
      socket.dispose()
      socketRef.current = null
      reset()
    }
  }, [navigate, playerAvatarKey, playerId, playerName, reset, roomId])

  const self = snapshot?.players.find((player) => player.id === playerId)
  if (self && initialStack.current === null) initialStack.current = self.stack
  const canReady = self && self.status !== 'DISCONNECTED' && self.status !== 'BUSTED'
  const isOwner = snapshot?.ownerId === playerId
  const eligiblePlayers = snapshot?.players.filter((player) => player.status !== 'DISCONNECTED' && player.stack > 0) ?? []
  const allReady = eligiblePlayers.length >= 2 && eligiblePlayers.every((player) => player.ready)
  const canStart = isOwner && allReady && ['WAITING', 'READY', 'ROUND_END'].includes(snapshot?.phase ?? '')
  const observing = self?.status === 'SPECTATOR' || self?.status === 'BUSTED' || self?.status === 'DISCONNECTED'
  const positionedPlayers = snapshot ? positionPlayersForViewer(snapshot.players, playerId) : []
  const settlementVisible = Boolean(
    snapshot
    && snapshot.awards.length > 0
    && (snapshot.phase === 'SETTLEMENT' || snapshot.phase === 'ROUND_END')
    && dismissedSettlementHand !== snapshot.handId,
  )

  const startSettlementTransfer = useCallback((settledSnapshot: GameSnapshot) => {
    if (settlementStartedHand.current === settledSnapshot.handId) {
      setDismissedSettlementHand(settledSnapshot.handId)
      return
    }

    const winnings: Record<string, number> = {}
    settledSnapshot.awards.forEach((award) => {
      Object.entries(award.winnings).forEach(([winnerId, amount]) => {
        winnings[winnerId] = (winnings[winnerId] ?? 0) + amount
      })
    })
    const totalAwarded = Object.values(winnings).reduce((total, amount) => total + amount, 0)
    if (totalAwarded <= 0) return

    settlementStartedHand.current = settledSnapshot.handId
    setDismissedSettlementHand(settledSnapshot.handId)
    const duration = settlementTransferDuration(totalAwarded)
    const handId = settledSnapshot.handId
    const startedAt = performance.now()
    setSettlementAnimation({ handId, progress: 0, duration, winnings, active: true })

    const tick = (now: number) => {
      const linearProgress = Math.min(1, Math.max(0, (now - startedAt) / duration))
      setSettlementAnimation((current) => current?.handId === handId
        ? { ...current, progress: linearProgress, active: linearProgress < 1 }
        : current)
      if (linearProgress < 1) settlementFrame.current = window.requestAnimationFrame(tick)
      else settlementFrame.current = null
    }
    settlementFrame.current = window.requestAnimationFrame(tick)
  }, [])

  useEffect(() => {
    const handId = snapshot?.handId
    const status = self?.status
    if (handId !== previousHandId.current) {
      if (settlementFrame.current !== null) window.cancelAnimationFrame(settlementFrame.current)
      settlementFrame.current = null
      settlementStartedHand.current = null
      setSettlementAnimation(null)
      setDismissedSettlementHand(null)
      previousHandId.current = handId
      previousSelfStatus.current = status
      setAllInVisible(false)
      return
    }
    const becameAllIn = previousSelfStatus.current !== undefined
      && previousSelfStatus.current !== 'ALL_IN'
      && status === 'ALL_IN'
    previousSelfStatus.current = status
    if (!becameAllIn) return

    setAllInVisible(true)
    const timer = window.setTimeout(() => setAllInVisible(false), 3_200)
    return () => window.clearTimeout(timer)
  }, [self?.status, snapshot?.handId])

  useEffect(() => () => {
    if (settlementFrame.current !== null) window.cancelAnimationFrame(settlementFrame.current)
  }, [])

  useEffect(() => {
    if (!settlementVisible || !snapshot) return
    const settledSnapshot = snapshot
    const timer = window.setTimeout(
      () => startSettlementTransfer(settledSnapshot),
      SETTLEMENT_VERDICT_MAX_MS,
    )
    return () => window.clearTimeout(timer)
  }, [settlementVisible, snapshot, startSettlementTransfer])

  function leaveRoom() {
    if (accountProfile && roomId && self) {
      rememberPendingTableResult(roomId)
    } else if (self && initialStack.current !== null) {
      saveTableResult({
        netChips: self.stack - initialStack.current,
        buyIn: initialStack.current,
        returnedChips: self.stack,
      })
    }
    socketRef.current?.leave()
    navigate('/play')
  }

  function act(action: ActionType, amount = 0) {
    if (!snapshot) return
    socketRef.current?.act(snapshot.handId, snapshot.turnId, action, amount)
  }

  if (!roomId) return null

  const currentSettlement = settlementAnimation?.handId === snapshot?.handId ? settlementAnimation : null
  const displayedPot = snapshot && currentSettlement
    ? Math.max(0, Math.round(snapshot.pot * (1 - currentSettlement.progress)))
    : snapshot?.pot ?? 0

  return (
    <main className="table-shell">
      <header className="table-header">
        <button className="back-button" onClick={leaveRoom}><span aria-hidden="true">←</span> 离开牌桌</button>
        <div className="table-identity">
          <span className="table-logo">X</span>
          <div><strong>XIDAO TABLE</strong><small>房间 {roomId.slice(0, 8)}</small></div>
        </div>
        <ConnectionBadge state={connection} />
      </header>

      {lastError && <div className="table-toast error" role="alert"><span>{lastError}</span><button onClick={() => setError(null)}>×</button></div>}
      {notice && <div className="table-toast"><span>{notice}</span><button onClick={() => setNotice(null)}>×</button></div>}

      {!snapshot ? (
        <section className="table-loading">
          <div className="chip-loader"><span /><span /><span /></div>
          <h1>{connection === 'reconnecting' ? '正在恢复座位' : '正在进入牌桌'}</h1>
        </section>
      ) : (
        <>
          <section className="poker-room" aria-label="德州扑克牌桌">
            <div className="table-ritual-grid" aria-hidden="true" />
            <div className="table-meta">
              <span>{phaseLabels[snapshot.phase]}</span>
              <strong>第 {Math.max(snapshot.handId, 1)} 手</strong>
            </div>
            <div className="poker-table">
              <div className="felt-ring" aria-hidden="true" />
              <div className="table-center">
                <div className="pot-row">
                  <div
                    className={`pot-display${currentSettlement?.active ? ' is-draining' : ''}`}
                    data-pot-anchor
                  >
                    <small>总底池</small><strong>{displayedPot.toLocaleString()}</strong>
                  </div>
                  <DeckStack handId={snapshot.handId} phase={snapshot.phase} />
                </div>
                <div className="community-cards" aria-label="公共牌">
                  {Array.from({ length: 5 }, (_, index) => (
                    snapshot.communityCards[index]
                      ? <CardView card={snapshot.communityCards[index]} dealing dealDelay={index * 110} key={index} />
                      : <div className="card-slot" key={index} aria-hidden="true" />
                  ))}
                </div>
              </div>
              {positionedPlayers.map(({ player, position }) => (
                (() => {
                  const winnings = currentSettlement?.winnings[player.id] ?? 0
                  const displayStack = currentSettlement
                    ? Math.round(player.stack - winnings + winnings * currentSettlement.progress)
                    : player.stack
                  return (
                    <PlayerSeat
                      key={`${player.id}-${snapshot.handId}`}
                      player={player}
                      isActor={player.seat === snapshot.currentActorSeat}
                      isOwner={player.id === snapshot.ownerId}
                      isSelf={player.id === playerId}
                      position={position}
                      displayStack={displayStack}
                      stackAnimating={Boolean(currentSettlement?.active && winnings > 0)}
                    />
                  )
                })()
              ))}
            </div>
          </section>

          {(observing || (['WAITING', 'READY', 'ROUND_END'].includes(snapshot.phase) && canReady) || canStart) && (
            <section className="table-controls">
              {observing && <span className="mode-badge">观战</span>}
              {['WAITING', 'READY', 'ROUND_END'].includes(snapshot.phase) && canReady && (
                <button className={`ready-button${self.ready ? ' is-ready' : ''}`} onClick={() => socketRef.current?.setReady(!self.ready)}>
                  {self.ready ? '取消准备' : '准备'}
                </button>
              )}
              {canStart && (
                <button className="start-button" onClick={() => socketRef.current?.startGame(snapshot.handId)}>
                  {snapshot.handId > 0 ? '开始下一手' : '开始游戏'}
                </button>
              )}
            </section>
          )}

          <ActionBar snapshot={snapshot} playerId={playerId} onAction={act} effectKey={self?.cosmetics?.buttonEffect} />
          <AllInBroadcast
            visible={allInVisible}
            amount={self?.totalContribution ?? 0}
            onDismiss={() => setAllInVisible(false)}
            effectKey={self?.cosmetics?.buttonEffect}
          />
          <SettlementOverlay
            snapshot={snapshot}
            playerId={playerId}
            visible={settlementVisible}
            onClose={() => startSettlementTransfer(snapshot)}
          />
          <SettlementTransfer
            active={Boolean(currentSettlement?.active)}
            handId={snapshot.handId}
            duration={currentSettlement?.duration ?? 500}
            winnings={currentSettlement?.winnings ?? {}}
          />
        </>
      )}
    </main>
  )
}
