import { BUILD_VERSION, PROTOCOL_VERSION, buildVersionsCompatible } from '../config/protocol'
import type { ArenaConnectionState, ArenaSnapshot, VehicleControls } from '../arena/types'
import { createRandomId } from '../utils/randomId'

interface ArenaEnvelope {
  type: string
  roomId: string
  sequence: number | null
  payload: unknown
  protocolVersion: number
  buildVersion: string
}

interface ConnectionPayload { playerId: string; connectionEpoch: number; resumeToken: string }

export interface ArenaSocketCallbacks {
  onSnapshot: (snapshot: ArenaSnapshot, sequence: number) => void
  onConnection: (state: ArenaConnectionState) => void
  onError: (message: string) => void
}

export interface ArenaIdentity {
  playerId: string
  playerName: string
  avatarKey: string
}

export class ArenaSocket {
  private socket: WebSocket | null = null
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private reconnectAttempt = 0
  private manuallyClosed = false
  private inputSequence = 0

  constructor(
    private readonly roomId: string,
    private readonly identity: ArenaIdentity,
    private readonly callbacks: ArenaSocketCallbacks,
  ) {}

  connect() {
    this.manuallyClosed = false
    this.open()
  }

  close() {
    this.manuallyClosed = true
    this.clearTimers()
    this.socket?.close(1000, 'client closed')
    this.socket = null
    this.callbacks.onConnection('idle')
  }

  leave() {
    this.send('LEAVE', {}, true)
    this.manuallyClosed = true
  }

  ready(ready: boolean) { this.send('READY', { ready }, true) }
  start() { this.send('START', {}, true) }
  requestSnapshot() { this.send('REQUEST_SNAPSHOT', {}, true) }

  input(controls: VehicleControls) {
    this.inputSequence += 1
    this.send('INPUT', { sequence: this.inputSequence, ...controls }, true)
  }

  private open() {
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return
    this.callbacks.onConnection(this.reconnectAttempt === 0 ? 'connecting' : 'reconnecting')
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const query = new URLSearchParams({
      roomId: this.roomId,
      playerId: this.identity.playerId,
      playerName: this.identity.playerName,
      avatarKey: this.identity.avatarKey,
      protocolVersion: String(PROTOCOL_VERSION),
      buildVersion: BUILD_VERSION,
    })
    const resumeToken = sessionStorage.getItem(this.tokenKey())
    if (resumeToken) query.set('resumeToken', resumeToken)
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/arena?${query}`)
    this.socket = socket
    socket.onopen = () => {
      if (this.socket !== socket) return
      this.reconnectAttempt = 0
      this.callbacks.onConnection('connected')
      this.heartbeatTimer = window.setInterval(() => this.send('PING', {}, false), 12_000)
    }
    socket.onmessage = (event) => this.receive(event.data)
    socket.onerror = () => this.callbacks.onError('载具竞技连接发生异常')
    socket.onclose = (event) => {
      if (this.socket !== socket) return
      this.socket = null
      if (this.heartbeatTimer != null) window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
      if (isTerminalArenaClose(event.code)) {
        this.manuallyClosed = true
        this.callbacks.onConnection('failed')
        this.callbacks.onError(arenaCloseMessage(event.code, event.reason))
        return
      }
      if (!this.manuallyClosed) this.scheduleReconnect()
    }
  }

  private receive(raw: unknown) {
    if (typeof raw !== 'string') return
    try {
      const envelope = JSON.parse(raw) as ArenaEnvelope
      if (envelope.protocolVersion !== PROTOCOL_VERSION || !buildVersionsCompatible(envelope.buildVersion)) {
        this.callbacks.onError('页面版本与服务器不一致，请强制刷新')
        this.close()
        return
      }
      if (envelope.type === 'ARENA_CONNECTION_READY') {
        const payload = envelope.payload as ConnectionPayload
        if (payload.resumeToken) sessionStorage.setItem(this.tokenKey(), payload.resumeToken)
      } else if (envelope.type === 'ARENA_SNAPSHOT' && envelope.sequence != null) {
        this.callbacks.onSnapshot(normalizeArenaSnapshot(envelope.payload), envelope.sequence)
      } else if (envelope.type === 'ARENA_ERROR') {
        const payload = envelope.payload as { message?: string }
        this.callbacks.onError(payload.message ?? '载具竞技命令失败')
      }
    } catch {
      this.callbacks.onError('服务器返回了无法识别的载具状态')
    }
  }

  private send(type: string, payload: object, request: boolean) {
    if (this.socket?.readyState !== WebSocket.OPEN) return
    this.socket.send(JSON.stringify({
      type,
      ...(request ? { requestId: createRandomId('arena_') } : {}),
      payload,
    }))
  }

  private scheduleReconnect() {
    this.reconnectAttempt += 1
    if (this.reconnectAttempt > 12) {
      this.callbacks.onConnection('failed')
      this.callbacks.onError('自动重连失败，请返回竞技大厅后重新进入')
      return
    }
    this.callbacks.onConnection('reconnecting')
    const delay = Math.min(4_000, 500 * 1.55 ** (this.reconnectAttempt - 1))
    this.reconnectTimer = window.setTimeout(() => this.open(), delay)
  }

  private clearTimers() {
    if (this.reconnectTimer != null) window.clearTimeout(this.reconnectTimer)
    if (this.heartbeatTimer != null) window.clearInterval(this.heartbeatTimer)
    this.reconnectTimer = null
    this.heartbeatTimer = null
  }

  private tokenKey() { return `xidao-arena.resume.${this.roomId}.${this.identity.playerId}` }
}

export function isTerminalArenaClose(code: number): boolean {
  return code === 4001 || code === 1008
}

export function arenaCloseMessage(code: number, reason: string): string {
  if (code === 4001) return '该游戏 ID 已在另一个页面或设备进入此房间，请关闭重复页面或改用独立账号'
  if (code === 1008 && reason) return `连接被服务器拒绝：${reason}`
  if (code === 1008) return '连接被服务器拒绝，请重新登录并刷新页面'
  return '载具竞技连接已断开'
}

function normalizeArenaSnapshot(payload: unknown): ArenaSnapshot {
  const snapshot = payload as ArenaSnapshot
  return {
    ...snapshot,
    maxPlayers: Number.isFinite(snapshot?.maxPlayers) ? snapshot.maxPlayers : 10,
    projectileCapacity: Number.isFinite(snapshot?.projectileCapacity) ? snapshot.projectileCapacity : 5,
    walls: Array.isArray(snapshot?.walls) ? snapshot.walls : [],
    projectiles: Array.isArray(snapshot?.projectiles) ? snapshot.projectiles : [],
    skills: Array.isArray(snapshot?.skills) ? snapshot.skills : [],
    eliminations: Array.isArray(snapshot?.eliminations) ? snapshot.eliminations : [],
    skillSpawnInMillis: Number.isFinite(snapshot?.skillSpawnInMillis) ? snapshot.skillSpawnInMillis : 0,
    players: Array.isArray(snapshot?.players) ? snapshot.players.map((player) => ({
      ...player,
      speedBoosted: Boolean(player.speedBoosted),
      rapidFire: Boolean(player.rapidFire),
      shielded: Boolean(player.shielded),
    })) : [],
  }
}
