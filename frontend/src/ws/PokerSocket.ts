import { useGameStore } from '../store/gameStore'
import { createRandomId } from '../utils/randomId'
import type {
  ClientEnvelope,
  ConnectionReadyPayload,
  ErrorPayload,
  EventReplayPayload,
  GameEvent,
  ServerEnvelope,
  SnapshotPayload,
} from '../types/protocol'

interface ConnectionCredentials {
  connectionEpoch: number
  resumeToken: string
}

interface ConnectOptions {
  roomId: string
  playerId: string
  playerName: string
}

const EVENT_TYPES = new Set([
  'PLAYER_JOINED',
  'PLAYER_LEFT',
  'READY_CHANGED',
  'OWNER_CHANGED',
  'GAME_STARTED',
  'HAND_STARTED',
  'BLINDS_POSTED',
  'HOLE_CARDS_DEALT',
  'TURN_CHANGED',
  'PLAYER_ACTION',
  'PHASE_CHANGED',
  'COMMUNITY_CARD_UPDATED',
  'PLAYER_DISCONNECTED',
  'PLAYER_RECONNECTED',
  'SHOWDOWN',
  'SETTLEMENT',
  'PLAYER_BUSTED',
  'HAND_ENDED',
])

const TERMINAL_CONNECTION_ERRORS = new Set([
  'INVALID_CONNECTION',
  'MEMBER_ALREADY_CONNECTED',
  'MEMBER_NOT_CONNECTED',
  'ROOM_NOT_FOUND',
  'ROOM_CLOSED',
  'STALE_CONNECTION',
])
const MAX_RECONNECT_ATTEMPTS = 8

export function isTerminalConnectionError(code: string): boolean {
  return TERMINAL_CONNECTION_ERRORS.has(code)
}

export function reconnectAttemptsExhausted(attempts: number): boolean {
  return attempts >= MAX_RECONNECT_ATTEMPTS
}

function socketBaseUrl(): string {
  const configured = import.meta.env.VITE_WS_BASE_URL?.replace(/\/$/, '')
  if (configured) return configured
  return window.location.origin.replace(/^http/, 'ws')
}

function credentialKey(roomId: string, playerId: string): string {
  return `xidao-poker.connection.${roomId}.${playerId}`
}

function loadCredentials(roomId: string, playerId: string): ConnectionCredentials | null {
  const raw = sessionStorage.getItem(credentialKey(roomId, playerId))
  if (!raw) return null
  try {
    const value = JSON.parse(raw) as Partial<ConnectionCredentials>
    if (
      typeof value.connectionEpoch === 'number' &&
      value.connectionEpoch > 0 &&
      typeof value.resumeToken === 'string' &&
      value.resumeToken.length > 0
    ) {
      return { connectionEpoch: value.connectionEpoch, resumeToken: value.resumeToken }
    }
  } catch {
    sessionStorage.removeItem(credentialKey(roomId, playerId))
  }
  return null
}

function saveCredentials(roomId: string, playerId: string, value: ConnectionCredentials): void {
  sessionStorage.setItem(credentialKey(roomId, playerId), JSON.stringify(value))
}

function clearCredentials(roomId: string, playerId: string): void {
  sessionStorage.removeItem(credentialKey(roomId, playerId))
}

export class PokerSocket {
  private socket: WebSocket | null = null
  private options: ConnectOptions | null = null
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private reconnectAttempt = 0
  private intentionallyClosed = false
  private replayRequested = false

  connect(options: ConnectOptions): void {
    this.closeSocketOnly()
    this.options = options
    this.intentionallyClosed = false
    this.open()
  }

  setReady(ready: boolean): void {
    this.send('READY', { ready })
  }

  startGame(expectedPreviousHandId: number): void {
    this.send('START_GAME', { expectedPreviousHandId })
  }

  act(handId: number, turnId: number, action: string, amount = 0): void {
    this.send('PLAYER_ACTION', { handId, turnId, action, amount })
  }

  requestSnapshot(): void {
    this.send('REQUEST_SNAPSHOT', {})
  }

  leave(): void {
    if (this.options && this.socket?.readyState === WebSocket.OPEN) {
      this.send('LEAVE', {})
      clearCredentials(this.options.roomId, this.options.playerId)
    }
    this.intentionallyClosed = true
    this.closeSocketOnly()
    useGameStore.getState().setConnection('closed')
  }

  dispose(): void {
    this.intentionallyClosed = true
    this.closeSocketOnly()
  }

  private open(): void {
    if (!this.options) return
    const { roomId, playerId, playerName } = this.options
    const credentials = loadCredentials(roomId, playerId)
    const params = new URLSearchParams({ roomId, playerId })
    if (credentials) {
      params.set('connectionEpoch', String(credentials.connectionEpoch))
      params.set('resumeToken', credentials.resumeToken)
    } else {
      params.set('playerName', playerName)
    }

    useGameStore.getState().setConnection(this.reconnectAttempt === 0 ? 'connecting' : 'reconnecting')
    const socket = new WebSocket(`${socketBaseUrl()}/ws/poker?${params.toString()}`)
    this.socket = socket

    socket.addEventListener('open', () => {
      if (socket !== this.socket) return
      this.reconnectAttempt = 0
      useGameStore.getState().setConnection('connected')
      useGameStore.getState().setError(null)
      this.startHeartbeat()
    })
    socket.addEventListener('message', (message) => {
      if (socket !== this.socket || typeof message.data !== 'string') return
      this.handleMessage(message.data)
    })
    socket.addEventListener('close', () => {
      if (socket !== this.socket) return
      this.stopHeartbeat()
      this.socket = null
      if (!this.intentionallyClosed) this.scheduleReconnect()
    })
    socket.addEventListener('error', () => {
      if (socket === this.socket) useGameStore.getState().setError('连接牌桌失败，正在尝试重连…')
    })
  }

  private handleMessage(raw: string): void {
    let envelope: ServerEnvelope
    try {
      envelope = JSON.parse(raw) as ServerEnvelope
    } catch {
      useGameStore.getState().setError('收到无法识别的服务器消息')
      return
    }

    if (envelope.type === 'CONNECTION_READY') {
      this.acceptCredentials(envelope.payload as ConnectionReadyPayload)
      // 定向快照可能与连接提交并发到达；确认身份后主动补请求可安全兜底。
      this.requestSnapshot()
      return
    }
    if (envelope.type === 'ROOM_SNAPSHOT') {
      const payload = envelope.payload as SnapshotPayload
      this.acceptCredentials(payload)
      useGameStore.getState().replaceSnapshot(payload.state)
      this.replayRequested = false
      return
    }
    if (envelope.type === 'EVENT_REPLAY') {
      this.applyReplay(envelope.payload as EventReplayPayload)
      return
    }
    if (envelope.type === 'ERROR') {
      const error = envelope.payload as ErrorPayload
      useGameStore.getState().setError(`${error.code}：${error.message}`)
      if (isTerminalConnectionError(error.code) && this.options) {
        clearCredentials(this.options.roomId, this.options.playerId)
        this.intentionallyClosed = true
        this.closeSocketOnly()
        useGameStore.getState().setConnection('closed')
      }
      return
    }
    if (EVENT_TYPES.has(envelope.type)) {
      this.applyEvent(envelope.payload as GameEvent)
    }
  }

  private acceptCredentials(payload: Pick<ConnectionReadyPayload, 'connectionEpoch' | 'resumeToken'>): void {
    if (!this.options) return
    saveCredentials(this.options.roomId, this.options.playerId, {
      connectionEpoch: payload.connectionEpoch,
      resumeToken: payload.resumeToken,
    })
  }

  private applyEvent(event: GameEvent): void {
    const result = useGameStore.getState().applyEvent(event)
    if (result === 'gap' && !this.replayRequested) {
      this.replayRequested = true
      const afterSequence = useGameStore.getState().snapshot?.lastSequence ?? 0
      this.send('REPLAY_EVENTS', { afterSequence })
    } else if (result === 'no-snapshot') {
      this.requestSnapshot()
    }
  }

  private applyReplay(replay: EventReplayPayload): void {
    this.replayRequested = false
    if (replay.snapshotRequired) {
      this.requestSnapshot()
      return
    }
    for (const event of replay.events) {
      const result = useGameStore.getState().applyEvent(event)
      if (result === 'gap' || result === 'no-snapshot') {
        this.requestSnapshot()
        return
      }
    }
  }

  private send(type: ClientEnvelope['type'], payload: Record<string, unknown>): void {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      useGameStore.getState().setError('当前未连接到牌桌')
      return
    }
    const envelope: ClientEnvelope = { type, commandId: createRandomId('cmd_'), payload }
    this.socket.send(JSON.stringify(envelope))
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null) return
    if (reconnectAttemptsExhausted(this.reconnectAttempt)) {
      this.intentionallyClosed = true
      useGameStore.getState().setConnection('closed')
      useGameStore.getState().setError('连接多次被拒绝，请确认前后端已启动后刷新页面')
      return
    }
    useGameStore.getState().setConnection('reconnecting')
    const delay = Math.min(500 * 2 ** this.reconnectAttempt, 5_000)
    this.reconnectAttempt += 1
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.open()
    }, delay)
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => this.send('PING', {}), 15_000)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== null) window.clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = null
  }

  private closeSocketOnly(): void {
    if (this.reconnectTimer !== null) window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
    this.stopHeartbeat()
    const socket = this.socket
    this.socket = null
    if (socket && socket.readyState < WebSocket.CLOSING) socket.close()
  }
}
