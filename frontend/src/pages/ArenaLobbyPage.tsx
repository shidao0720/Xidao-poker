import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { arenaApi } from '../api/arena'
import type { ArenaRoomSummary } from '../arena/types'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'
import { useAccountStore } from '../store/accountStore'
import { getPlayerName, savePlayerName } from '../utils/identity'

export function ArenaLobbyPage() {
  const navigate = useNavigate()
  const accountMode = useAccountStore((state) => state.mode === 'authenticated')
  const [rooms, setRooms] = useState<ArenaRoomSummary[]>([])
  const [roomName, setRoomName] = useState('逆相训练场')
  const [maxPlayers, setMaxPlayers] = useState(10)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try { setRooms(await arenaApi.listRooms()) } catch (reason) {
      setError(reason instanceof Error ? reason.message : '无法读取竞技房间')
    }
  }, [])

  useEffect(() => {
    void refresh()
    const timer = window.setInterval(() => void refresh(), 1_500)
    return () => window.clearInterval(timer)
  }, [refresh])

  async function createRoom() {
    if (!roomName.trim() || busy) return
    try {
      if (!accountMode) savePlayerName(getPlayerName())
      setBusy(true); setError(null)
      const room = await arenaApi.createRoom(roomName.trim(), maxPlayers)
      navigate(`/arena/${room.roomId}`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '创建竞技房间失败')
    } finally { setBusy(false) }
  }

  function enterRoom(roomId: string) {
    try {
      if (!accountMode) savePlayerName(getPlayerName())
      navigate(`/arena/${roomId}`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '请输入昵称')
    }
  }

  return (
    <main className="portal-page arena-lobby-page">
      <LobbySignalField />
      <SiteHeader />
      <section className="portal-content arena-lobby-content">
        <header className="arena-lobby-hero">
          <div><span>VECTOR ARENA / LAN PROTOTYPE 01</span><h1>逆相载具<br /><em>训练协议</em></h1></div>
          <p>最多十名驾驶员进入同一座反射迷宫。所有轨迹、碰撞和命中由主机统一裁决。</p>
        </header>
        <section className="arena-lobby-grid">
          <aside className="arena-create-panel">
            <span className="portal-code">CREATE SESSION</span><h2>建立训练场</h2>
            <label><span>房间名称</span><input id="arena-room-name" name="arenaRoomName" maxLength={40} value={roomName} onChange={(event) => setRoomName(event.target.value)} /></label>
            <label><span>最大驾驶员</span><input id="arena-max-players" name="arenaMaxPlayers" type="range" min={2} max={10} value={maxPlayers} onChange={(event) => setMaxPlayers(Number(event.target.value))} /><strong>{maxPlayers}</strong></label>
            <button disabled={busy || !roomName.trim()} onClick={createRoom}>{busy ? '建立中…' : '创建竞技房间'}<i>→</i></button>
          </aside>
          <section className="arena-room-browser">
            <header><div><span className="portal-code">ACTIVE SESSIONS</span><h2>局域网训练场</h2></div><button onClick={() => void refresh()}>刷新</button></header>
            {rooms.length === 0 ? <div className="arena-room-empty"><i>◇</i><strong>等待第一座训练场建立</strong></div> : (
              <div className="arena-room-list">{rooms.map((room) => (
                <article key={room.roomId}>
                  <div className="arena-room-sigil">{room.phase === 'RUNNING' ? 'R' : room.connectedPlayers}</div>
                  <div><small>{room.roomId.toUpperCase()}</small><h3>{room.roomName}</h3><p><span>{room.phase === 'RUNNING' ? '战斗中' : room.phase === 'ROUND_OVER' ? '结算中' : '等待中'}</span> 驾驶员 {room.connectedPlayers}/{room.maxPlayers}</p></div>
                  <button onClick={() => enterRoom(room.roomId)}>{room.phase === 'RUNNING' ? '观战/等待' : room.players >= room.maxPlayers ? '尝试重连' : '进入'}<i>→</i></button>
                </article>
              ))}</div>
            )}
          </section>
        </section>
        {error && <div className="market-notice" role="alert"><span>!</span><p>{error}</p><button onClick={() => setError(null)}>×</button></div>}
      </section>
    </main>
  )
}
