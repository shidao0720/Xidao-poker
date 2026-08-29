import type { PlayerSnapshot } from '../types/protocol'

export interface SeatPosition {
  x: number
  y: number
}

export interface PositionedPlayer {
  player: PlayerSnapshot
  position: SeatPosition
}

const TABLE_CENTER = 50
const HORIZONTAL_RADIUS = 43
const VERTICAL_RADIUS = 41

function rounded(value: number): number {
  return Math.round(value * 1000) / 1000
}

function positionOnTable(index: number, playerCount: number): SeatPosition {
  const angle = Math.PI / 2 + (Math.PI * 2 * index) / playerCount
  return {
    x: rounded(TABLE_CENTER + HORIZONTAL_RADIUS * Math.cos(angle)),
    y: rounded(TABLE_CENTER + VERTICAL_RADIUS * Math.sin(angle)),
  }
}

/**
 * 服务端 seat 只决定顺时针次序；客户端以查看者为视觉起点重新排布。
 * 因此每位玩家在自己的页面中始终位于底部，同时不改变真实行动顺序。
 */
export function positionPlayersForViewer(
  players: PlayerSnapshot[],
  viewerId: string,
): PositionedPlayer[] {
  if (players.length === 0) return []

  const clockwise = [...players].sort(
    (left, right) => left.seat - right.seat || left.id.localeCompare(right.id),
  )
  const foundViewerIndex = clockwise.findIndex((player) => player.id === viewerId)
  const viewerIndex = foundViewerIndex >= 0 ? foundViewerIndex : 0
  const relativeOrder = [
    ...clockwise.slice(viewerIndex),
    ...clockwise.slice(0, viewerIndex),
  ]

  return relativeOrder.map((player, index) => ({
    player,
    position: positionOnTable(index, relativeOrder.length),
  }))
}
