import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { roomApi } from '../api/rooms'
import type { CreateRoomInput, GamePhase, RoomSummary } from '../types/protocol'
import { getPlayerName, savePlayerName } from '../utils/identity'

const phaseLabels: Record<GamePhase, string> = {
  WAITING: '等待加入',
  READY: '等待开始',
  DEALING: '发牌中',
  PREFLOP: '翻牌前',
  FLOP: '翻牌',
  TURN: '转牌',
  RIVER: '河牌',
  SHOWDOWN: '摊牌',
  SETTLEMENT: '结算中',
  ROUND_END: '本手结束',
}

const initialForm: CreateRoomInput = {
  roomName: '周末牌局',
  smallBlind: 5,
  bigBlind: 10,
  buyIn: 1000,
  maxPlayers: 10,
}

export function LobbyPage() {
  const navigate = useNavigate()
  const [rooms, setRooms] = useState<RoomSummary[]>([])
  const [name, setName] = useState(getPlayerName())
  const [form, setForm] = useState(initialForm)
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadRooms = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    try {
      setRooms(await roomApi.list())
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '无法获取房间列表')
    } finally {
      if (!quiet) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadRooms()
    const timer = window.setInterval(() => void loadRooms(true), 3_000)
    return () => window.clearInterval(timer)
  }, [loadRooms])

  function enterRoom(roomId: string) {
    try {
      savePlayerName(name)
      navigate(`/rooms/${encodeURIComponent(roomId)}`)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '请输入昵称')
    }
  }

  async function createRoom(event: FormEvent) {
    event.preventDefault()
    try {
      savePlayerName(name)
      setCreating(true)
      const room = await roomApi.create(form)
      navigate(`/rooms/${encodeURIComponent(room.roomId)}`)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '创建房间失败')
    } finally {
      setCreating(false)
    }
  }

  return (
    <main className="lobby-shell">
      <header className="lobby-header">
        <a className="brand" href="/" aria-label="Xidao Poker 大厅">
          <span className="brand-mark">X</span>
          <span><strong>XIDAO</strong><small>POKER</small></span>
        </a>
        <label className="player-name-field">
          <span>你的昵称</span>
          <input
            id="player-name"
            name="playerName"
            autoComplete="nickname"
            value={name}
            maxLength={32}
            placeholder="输入昵称"
            onChange={(event) => setName(event.target.value)}
          />
        </label>
      </header>

      <section className="lobby-hero">
        <div>
          <p className="eyebrow">LOCAL TABLE · REAL-TIME PLAY</p>
          <h1>同一张桌，<br /><em>零距离开局。</em></h1>
          <p>为局域网聚会打造的德州扑克。服务端裁决每一步规则，最多十人同时入座。</p>
        </div>
        <div className="hero-stats" aria-label="项目特性">
          <div><strong>10</strong><span>最大座位</span></div>
          <div><strong>30s</strong><span>断线保护</span></div>
          <div><strong>52</strong><span>标准牌组</span></div>
        </div>
      </section>

      {error && <div className="alert" role="alert"><span>!</span>{error}<button onClick={() => setError(null)}>关闭</button></div>}

      <div className="lobby-grid">
        <section className="room-section">
          <div className="section-heading">
            <div><span className="eyebrow">GAME LOBBY</span><h2>正在等待的牌桌</h2></div>
            <button className="ghost-button" onClick={() => void loadRooms()} disabled={loading}>刷新</button>
          </div>

          <div className="room-list" aria-live="polite">
            {loading && rooms.length === 0 ? (
              <div className="empty-state"><div className="loader" /><p>正在寻找局域网牌桌…</p></div>
            ) : rooms.length === 0 ? (
              <div className="empty-state"><span className="empty-suits">♠ ♥ ♦ ♣</span><h3>大厅还是空的</h3><p>创建第一张牌桌，朋友们就能通过局域网加入。</p></div>
            ) : rooms.map((room) => (
              <article className="room-card" key={room.roomId}>
                <div className="room-card-top">
                  <span className={`phase-tag phase-${room.phase.toLowerCase()}`}>{phaseLabels[room.phase]}</span>
                  <span className="room-players">{room.connectedCount}/{room.maxPlayers} 在线</span>
                </div>
                <h3>{room.roomName}</h3>
                <p className="room-id">房间号 {room.roomId.slice(0, 8)}</p>
                <div className="room-rules">
                  <span><small>盲注</small>{room.smallBlind}/{room.bigBlind}</span>
                  <span><small>买入</small>{room.buyIn.toLocaleString()}</span>
                  <span><small>席位</small>{room.playerCount}/{room.maxPlayers}</span>
                </div>
                <button className="primary-button" onClick={() => enterRoom(room.roomId)} disabled={room.playerCount >= room.maxPlayers}>
                  {room.playerCount >= room.maxPlayers ? '房间已满' : room.phase === 'WAITING' || room.phase === 'READY' ? '入座' : '进入观战'}
                  <span aria-hidden="true">→</span>
                </button>
              </article>
            ))}
          </div>
        </section>

        <aside className="create-panel">
          <span className="eyebrow">HOST A TABLE</span>
          <h2>创建新牌桌</h2>
          <p>设置本场规则。开局后，新加入的玩家会安全进入观战状态。</p>
          <form onSubmit={(event) => void createRoom(event)}>
            <label><span>房间名称</span><input id="room-name" name="roomName" required maxLength={40} value={form.roomName} onChange={(event) => setForm({ ...form, roomName: event.target.value })} /></label>
            <div className="form-row">
              <label><span>小盲</span><input id="small-blind" name="smallBlind" required min={1} type="number" value={form.smallBlind} onChange={(event) => setForm({ ...form, smallBlind: Number(event.target.value) })} /></label>
              <label><span>大盲</span><input id="big-blind" name="bigBlind" required min={2} type="number" value={form.bigBlind} onChange={(event) => setForm({ ...form, bigBlind: Number(event.target.value) })} /></label>
            </div>
            <div className="form-row">
              <label><span>初始筹码</span><input id="buy-in" name="buyIn" required min={2} type="number" value={form.buyIn} onChange={(event) => setForm({ ...form, buyIn: Number(event.target.value) })} /></label>
              <label><span>最大人数</span><select id="max-players" name="maxPlayers" value={form.maxPlayers} onChange={(event) => setForm({ ...form, maxPlayers: Number(event.target.value) })}>{Array.from({ length: 9 }, (_, index) => index + 2).map((value) => <option value={value} key={value}>{value} 人</option>)}</select></label>
            </div>
            <button className="primary-button create-button" disabled={creating}>{creating ? '正在创建…' : '创建并入座'}<span>＋</span></button>
          </form>
        </aside>
      </div>
    </main>
  )
}
