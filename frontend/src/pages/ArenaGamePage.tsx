import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArenaCanvas } from '../components/ArenaCanvas'
import { AvatarView } from '../components/AvatarView'
import { useAccountStore } from '../store/accountStore'
import { useArenaStore } from '../store/arenaStore'
import { getPlayerId, getPlayerName } from '../utils/identity'
import type { ArenaElimination, ArenaSnapshot, VehicleControls } from '../arena/types'

export function ArenaGamePage() {
  const { roomId = '' } = useParams()
  const navigate = useNavigate()
  const profile = useAccountStore((state) => state.profile)
  const mode = useAccountStore((state) => state.mode)
  const { snapshot, connection, error, connect, disconnect, leave, ready, start, input, clearError } = useArenaStore()
  const playerId = profile?.gameId ?? getPlayerId()
  const playerName = profile?.gameId ?? getPlayerName()
  const avatarKey = profile?.avatarKey ?? 'default'
  const [eliminationNotice, setEliminationNotice] = useState<ArenaElimination | null>(null)
  const lastEliminationId = useRef(0)
  const latestEliminationRef = useRef<ArenaElimination | undefined>(undefined)

  useEffect(() => {
    if (!roomId || !playerName) { navigate('/arena', { replace: true }); return }
    connect(roomId, { playerId, playerName, avatarKey })
    return () => disconnect()
  }, [avatarKey, connect, disconnect, navigate, playerId, playerName, roomId])

  const sendInput = useCallback((controls: VehicleControls) => input(controls), [input])
  const self = snapshot?.players.find((player) => player.id === playerId)
  const connected = snapshot?.players.filter((player) => player.connected) ?? []
  const projectileCapacity = snapshot?.projectileCapacity ?? 5
  const ammunition = Math.max(0, projectileCapacity
    - (snapshot?.projectiles.filter((projectile) => projectile.ownerId === playerId).length ?? 0))
  const owner = snapshot?.ownerId === playerId
  const everyoneReady = connected.length >= 2 && connected.every((player) => player.ready)
  const latestElimination = snapshot?.eliminations.at(-1)
  latestEliminationRef.current = latestElimination

  useEffect(() => {
    const latest = latestEliminationRef.current
    if (!latest || latest.id <= lastEliminationId.current) return
    lastEliminationId.current = latest.id
    setEliminationNotice(latest)
    const timer = window.setTimeout(() => setEliminationNotice((current) =>
      current?.id === latest.id ? null : current), 3_000)
    return () => window.clearTimeout(timer)
  }, [latestElimination?.id])

  useEffect(() => {
    lastEliminationId.current = 0
    setEliminationNotice(null)
  }, [roomId])

  function exit() {
    leave()
    navigate('/arena')
  }

  if (!snapshot) return (
    <main className="arena-game-page"><div className="arena-boot"><span /><strong>{connection === 'failed' ? '连接失败' : '正在接入逆相训练场'}</strong><button onClick={() => navigate('/arena')}>返回大厅</button></div></main>
  )

  return (
    <main className="arena-game-page">
      <header className="arena-game-header">
        <button onClick={exit}>← 退出训练场</button>
        <div><small>VECTOR ARENA / {snapshot.roomId}</small><strong>{snapshot.roomName}</strong></div>
        <p data-state={connection}><i />{connection === 'connected' ? '同步正常' : connection === 'reconnecting' ? '正在重连' : '连接中'}</p>
      </header>
      <section className="arena-stage-layout">
        <aside className="arena-scoreboard">
          <header><small>DRIVERS</small><strong>{snapshot.players.length}/{snapshot.maxPlayers}</strong></header>
          {snapshot.players.map((player) => <div key={player.id} className={`${player.id === playerId ? 'is-self' : ''} ${player.lifeState === 'ELIMINATED' ? 'is-out' : ''}`}>
            <AvatarView avatarKey={player.avatarKey} name={player.name} />
            <span><strong>{player.name}</strong><small>{player.lifeState === 'ALIVE' ? '战斗中' : player.lifeState === 'SPECTATING' ? '观战' : player.ready ? '已准备' : player.lifeState === 'ELIMINATED' ? '已淘汰' : '等待'}</small>
              {(player.speedBoosted || player.rapidFire || player.shielded) && <em className="arena-skill-tags">
                {player.speedBoosted && <i title="超载推进">»</i>}
                {player.rapidFire && <i title="快速射击">✦</i>}
                {player.shielded && <i title="灵子护盾">⬡</i>}
              </em>}
            </span>
            <b key={`${player.id}-${player.wins}`} className="arena-win-counter">{player.wins}<small>W</small></b>
          </div>)}
        </aside>
        <section className="arena-stage">
          <ArenaCanvas snapshot={snapshot} playerId={playerId} onInput={sendInput} />
          {snapshot.phase === 'WAITING' && <div className="arena-wait-overlay">
            <span>ROUND {String(snapshot.roundId + 1).padStart(2, '0')}</span>
            <h2>等待驾驶员同步</h2>
            <p>{connected.filter((player) => player.ready).length}/{connected.length} READY</p>
            {self && <button className={self.ready ? 'is-ready' : ''} onClick={() => ready(!self.ready)}>{self.ready ? '取消准备' : '准备完毕'}</button>}
            {owner && <button className="arena-start-button" disabled={!everyoneReady} onClick={start}>开始本轮</button>}
          </div>}
          {eliminationNotice && <EliminationBroadcast elimination={eliminationNotice} snapshot={snapshot} />}
          {snapshot.phase === 'RUNNING' && self?.lifeState === 'SPECTATING' && <div className="arena-spectator-banner">当前回合进行中 · 下一轮自动加入</div>}
        </section>
        <aside className="arena-telemetry">
          <section><small>ROUND</small><strong>{String(snapshot.roundId).padStart(2, '0')}</strong></section>
          <section><small>SURVIVORS</small><strong>{snapshot.players.filter((player) => player.lifeState === 'ALIVE').length}</strong></section>
          <section><small>PROJECTILES</small><strong>{snapshot.projectiles.length}</strong></section>
          <section><small>AMMO</small><strong>{ammunition}/{projectileCapacity}</strong></section>
          <section className="arena-skill-timer"><small>NEXT SKILL</small><strong>{snapshot.phase === 'RUNNING' ? formatCountdown(snapshot.skillSpawnInMillis) : '--:--'}</strong></section>
          <div className="arena-skill-legend"><i>»</i><span>超载推进</span></div>
          <div className="arena-skill-legend"><i>✦</i><span>快速射击</span></div>
          <div className="arena-skill-legend"><i>⬡</i><span>灵子护盾</span></div>
          <div><kbd>W</kbd><kbd>S</kbd><span>前进 / 后退</span></div>
          <div><kbd>A</kbd><kbd>D</kbd><span>左转 / 右转</span></div>
          <div><kbd>SPACE</kbd><span>开火</span></div>
        </aside>
      </section>
      {error && <div className="arena-error" role="alert"><span>{error}</span><button onClick={clearError}>×</button></div>}
      <footer className="arena-game-footer"><span>SERVER AUTHORITATIVE</span><span>30 TICK/S</span><span>{mode === 'authenticated' ? 'IDENTITY VERIFIED' : 'LAN GUEST'}</span></footer>
    </main>
  )
}

function EliminationBroadcast({ elimination, snapshot }: {
  elimination: ArenaElimination
  snapshot: ArenaSnapshot
}) {
  const victim = snapshot.players.find((player) => player.id === elimination.victimId)
  const attacker = snapshot.players.find((player) => player.id === elimination.attackerId)
  const selfDestruct = elimination.victimId === elimination.attackerId
  return (
    <div className="arena-elimination-broadcast" key={elimination.id}>
      <small>SPIRIT FRAME LOST</small>
      <strong>{victim?.name ?? elimination.victimId}</strong>
      <span>{selfDestruct ? '被反射弹反噬' : <>由 <b>{attacker?.name ?? elimination.attackerId}</b> 淘汰</>}</span>
    </div>
  )
}

function formatCountdown(milliseconds: number) {
  const seconds = Math.max(0, Math.ceil(milliseconds / 1_000))
  return `00:${String(seconds).padStart(2, '0')}`
}
