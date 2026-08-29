import { createRandomId } from './randomId'

const PLAYER_ID_KEY = 'xidao-poker.player-id'
const PLAYER_NAME_KEY = 'xidao-poker.player-name'

function newPlayerId(): string {
  return createRandomId('p_')
}

export function getPlayerId(): string {
  const existing = localStorage.getItem(PLAYER_ID_KEY)
  if (existing && /^[A-Za-z0-9_-]{1,64}$/.test(existing)) return existing
  const created = newPlayerId()
  localStorage.setItem(PLAYER_ID_KEY, created)
  return created
}

export function getPlayerName(): string {
  return localStorage.getItem(PLAYER_NAME_KEY)?.trim().slice(0, 32) ?? ''
}

export function savePlayerName(name: string): string {
  const normalized = name.trim().slice(0, 32)
  if (!normalized) throw new Error('请输入玩家昵称')
  localStorage.setItem(PLAYER_NAME_KEY, normalized)
  return normalized
}

export function applyAccountIdentity(gameId: string): void {
  const normalized = gameId.trim()
  if (normalized.length < 1 || normalized.length > 12) throw new Error('游戏 ID 无效')
  localStorage.setItem(PLAYER_ID_KEY, normalized)
  localStorage.setItem(PLAYER_NAME_KEY, normalized.slice(0, 32))
}
