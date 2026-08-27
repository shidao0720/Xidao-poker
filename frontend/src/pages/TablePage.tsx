import { useEffect, useMemo, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ActionBar } from '../components/ActionBar'
import { CardView } from '../components/CardView'
import { ConnectionBadge } from '../components/ConnectionBadge'
import { PlayerSeat } from '../components/PlayerSeat'
import { useGameStore } from '../store/gameStore'
import type { ActionType, GamePhase } from '../types/protocol'
import { getPlayerId, getPlayerName } from '../utils/identity'
import { PokerSocket } from '../ws/PokerSocket'

const seatPositions = [
  { x: 50, y: 91 },
  { x: 23, y: 86 },
  { x: 7, y: 67 },
  { x: 7, y: 34 },
  { x: 25, y: 12 },
  { x: 50, y: 7 },
  { x: 75, y: 12 },
  { x: 93, y: 34 },
  { x: 93, y: 67 },
  { x: 77, y: 86 },
]

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

export function TablePage() {
  const { roomId } = useParams()
  const navigate = useNavigate()
  const socketRef = useRef<PokerSocket | null>(null)
  const playerId = useMemo(getPlayerId, [])
  const playerName = useMemo(getPlayerName, [])
  const { snapshot, connection, lastError, notice, setError, setNotice, reset } = useGameStore()

  useEffect(() => {
    if (!roomId || !playerName) {
      navigate('/', { replace: true })
      return
    }
    const socket = new PokerSocket()
    socketRef.current = socket
    // 延迟一个任务周期，避免 React StrictMode 的开发期 setup/cleanup 探测创建幽灵连接。
    const connectTimer = window.setTimeout(() => {
      socket.connect({ roomId, playerId, playerName })
    }, 0)
    return () => {
      window.clearTimeout(connectTimer)
      socket.dispose()
      socketRef.current = null
      reset()
    }
  }, [navigate, playerId, playerName, reset, roomId])

  const self = snapshot?.players.find((player) => player.id === playerId)
  const canReady = self && self.status !== 'DISCONNECTED' && self.status !== 'BUSTED'
  const isOwner = snapshot?.ownerId === playerId
  const eligiblePlayers = snapshot?.players.filter((player) => player.status !== 'DISCONNECTED' && player.stack > 0) ?? []
  const allReady = eligiblePlayers.length >= 2 && eligiblePlayers.every((player) => player.ready)
  const canStart = isOwner && allReady && ['WAITING', 'READY', 'ROUND_END'].includes(snapshot?.phase ?? '')
  const observing = self?.status === 'SPECTATOR' || self?.status === 'BUSTED' || self?.status === 'DISCONNECTED'

  function leaveRoom() {
    socketRef.current?.leave()
    navigate('/')
  }

  function act(action: ActionType, amount = 0) {
    if (!snapshot) return
    socketRef.current?.act(snapshot.handId, snapshot.turnId, action, amount)
  }

  if (!roomId) return null

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
          <h1>{connection === 'reconnecting' ? '正在恢复你的座位' : '正在进入牌桌'}</h1>
          <p>服务端将发送只属于你的安全快照。</p>
        </section>
      ) : (
        <>
          <section className="poker-room" aria-label="德州扑克牌桌">
            <div className="table-meta">
              <span>{phaseLabels[snapshot.phase]}</span>
              <strong>第 {Math.max(snapshot.handId, 1)} 手</strong>
            </div>
            <div className="poker-table">
              <div className="felt-ring" aria-hidden="true" />
              <div className="table-center">
                <div className="pot-display"><small>总底池</small><strong>{snapshot.pot.toLocaleString()}</strong></div>
                <div className="community-cards" aria-label="公共牌">
                  {Array.from({ length: 5 }, (_, index) => (
                    snapshot.communityCards[index]
                      ? <CardView card={snapshot.communityCards[index]} key={index} />
                      : <div className="card-slot" key={index} aria-hidden="true" />
                  ))}
                </div>
                <div className="street-label">{phaseLabels[snapshot.phase]}</div>
              </div>
              {snapshot.players.map((player) => (
                <PlayerSeat
                  key={player.id}
                  player={player}
                  isActor={player.seat === snapshot.currentActorSeat}
                  isOwner={player.id === snapshot.ownerId}
                  isSelf={player.id === playerId}
                  position={seatPositions[player.seat % seatPositions.length] ?? seatPositions[0]!}
                />
              ))}
            </div>
          </section>

          <section className="table-controls">
            <div className="hand-status">
              {observing ? (
                <><span className="watch-icon">◉</span><div><strong>观战模式</strong><small>你不在本手行动队列中，不会阻塞游戏</small></div></>
              ) : snapshot.currentActorSeat === self?.seat ? (
                <><span className="turn-icon">↗</span><div><strong>轮到你行动</strong><small>请按服务端给出的合法操作选择</small></div></>
              ) : (
                <><span className="watch-icon">◎</span><div><strong>{snapshot.currentActorSeat === null ? phaseLabels[snapshot.phase] : '等待其他玩家'}</strong><small>{self?.ready ? '你已准备' : '牌桌状态由服务端实时同步'}</small></div></>
              )}
            </div>
            {['WAITING', 'READY', 'ROUND_END'].includes(snapshot.phase) && canReady && (
              <button className={`ready-button${self.ready ? ' is-ready' : ''}`} onClick={() => socketRef.current?.setReady(!self.ready)}>
                {self.ready ? '取消准备' : '准备'}
              </button>
            )}
            {canStart && (
              <button className="start-button" onClick={() => socketRef.current?.startGame(snapshot.handId)}>
                {snapshot.handId > 0 ? '开始下一手' : '房主开始游戏'}
              </button>
            )}
          </section>

          <ActionBar snapshot={snapshot} playerId={playerId} onAction={act} />
        </>
      )}
    </main>
  )
}
