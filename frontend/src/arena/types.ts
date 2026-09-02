export type ArenaPhase = 'WAITING' | 'RUNNING' | 'ROUND_OVER'
export type ArenaLifeState = 'WAITING' | 'ALIVE' | 'ELIMINATED' | 'SPECTATING'
export type ArenaSkillType = 'OVERDRIVE' | 'RAPID_FIRE' | 'AEGIS'

export interface ArenaRoomSummary {
  roomId: string
  roomName: string
  phase: ArenaPhase
  players: number
  connectedPlayers: number
  maxPlayers: number
  createdAt: string
}

export interface ArenaWall { x: number; y: number; width: number; height: number }

export interface ArenaPlayer {
  id: string
  name: string
  avatarKey: string
  seat: number
  connected: boolean
  ready: boolean
  lifeState: ArenaLifeState
  x: number
  y: number
  angle: number
  kills: number
  wins: number
  speedBoosted: boolean
  rapidFire: boolean
  shielded: boolean
}

export interface ArenaProjectile {
  id: number
  ownerId: string
  x: number
  y: number
  velocityX: number
  velocityY: number
  bounces: number
}

export interface ArenaSkill {
  id: number
  type: ArenaSkillType
  x: number
  y: number
  expiresAt: number
}

export interface ArenaElimination {
  id: number
  roundId: number
  victimId: string
  attackerId: string
  occurredAt: number
}

export interface ArenaSnapshot {
  roomId: string
  roomName: string
  ownerId: string | null
  phase: ArenaPhase
  roundId: number
  tick: number
  width: number
  height: number
  maxPlayers: number
  projectileCapacity: number
  walls: ArenaWall[]
  players: ArenaPlayer[]
  projectiles: ArenaProjectile[]
  skills: ArenaSkill[]
  eliminations: ArenaElimination[]
  skillSpawnInMillis: number
  winnerId: string | null
  roundEndsAt: number
}

export interface VehicleControls {
  forward: boolean
  backward: boolean
  turnLeft: boolean
  turnRight: boolean
  fire: boolean
}

export type ArenaConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'failed'
