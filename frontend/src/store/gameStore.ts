import { create } from 'zustand'
import type {
  ActionOptions,
  ConnectionState,
  GameEvent,
  GamePhase,
  GameSnapshot,
  PlayerSnapshot,
  PlayerStatus,
  PotAward,
  RevealedHandSnapshot,
} from '../types/protocol'

export type EventApplyResult = 'applied' | 'duplicate' | 'gap' | 'no-snapshot'

interface GameState {
  connection: ConnectionState
  snapshot: GameSnapshot | null
  lastError: string | null
  notice: string | null
  setConnection: (connection: ConnectionState) => void
  replaceSnapshot: (snapshot: GameSnapshot) => void
  applyEvent: (event: GameEvent) => EventApplyResult
  setError: (message: string | null) => void
  setNotice: (message: string | null) => void
  reset: () => void
}

const noActions: ActionOptions = {
  legalActions: [],
  toCall: 0,
  callAmount: 0,
  minimumBetTo: null,
  minimumRaiseTo: null,
  maximumTo: 0,
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function booleanValue(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined
}

function updatePlayer(
  players: PlayerSnapshot[],
  playerId: string | null,
  updater: (player: PlayerSnapshot) => PlayerSnapshot,
): PlayerSnapshot[] {
  if (!playerId) return players
  return players.map((player) => (player.id === playerId ? updater(player) : player))
}

function phaseValue(value: unknown): GamePhase | undefined {
  return stringValue(value) as GamePhase | undefined
}

function statusValue(value: unknown): PlayerStatus | undefined {
  return stringValue(value) as PlayerStatus | undefined
}

function revealedHandsValue(value: unknown): RevealedHandSnapshot[] | undefined {
  return Array.isArray(value) ? value as RevealedHandSnapshot[] : undefined
}

function joinedPlayer(event: GameEvent): PlayerSnapshot | null {
  const id = event.playerId
  const name = stringValue(event.data.name)
  const seat = numberValue(event.data.seat)
  const stack = numberValue(event.data.stack)
  const streetBet = numberValue(event.data.streetBet)
  const totalContribution = numberValue(event.data.totalContribution)
  const status = statusValue(event.data.status)
  const inHand = booleanValue(event.data.inHand)
  const canAct = booleanValue(event.data.canAct)
  const ready = booleanValue(event.data.ready)

  if (
    !id || !name || seat === undefined || stack === undefined || streetBet === undefined ||
    totalContribution === undefined || status === undefined || inHand === undefined ||
    canAct === undefined || ready === undefined
  ) return null

  return {
    id,
    name,
    seat,
    stack,
    streetBet,
    totalContribution,
    status,
    inHand,
    canAct,
    ready,
    holeCards: [],
  }
}

function upsertPlayer(players: PlayerSnapshot[], player: PlayerSnapshot): PlayerSnapshot[] {
  return [...players.filter((current) => current.id !== player.id), player]
    .sort((left, right) => left.seat - right.seat)
}

export function reduceGameEvent(snapshot: GameSnapshot, event: GameEvent): GameSnapshot {
  const data = event.data
  let next: GameSnapshot = {
    ...snapshot,
    revealedHands: snapshot.revealedHands ?? [],
    lastSequence: event.sequence,
  }

  switch (event.type) {
    case 'PLAYER_JOINED': {
      const player = joinedPlayer(event)
      if (player) {
        next = {
          ...next,
          phase: phaseValue(data.phase) ?? next.phase,
          players: upsertPlayer(next.players, player),
        }
      }
      break
    }
    case 'READY_CHANGED':
      next = {
        ...next,
        phase: phaseValue(data.phase) ?? next.phase,
        players: updatePlayer(next.players, event.playerId, (player) => ({
          ...player,
          ready: data.ready === true,
        })),
      }
      break
    case 'OWNER_CHANGED':
      next = { ...next, ownerId: stringValue(data.ownerId) ?? event.playerId }
      break
    case 'PLAYER_LEFT':
      next = { ...next, players: next.players.filter((player) => player.id !== event.playerId) }
      break
    case 'HAND_STARTED':
      next = {
        ...next,
        handId: event.handId,
        phase: 'DEALING',
        buttonSeat: numberValue(data.buttonSeat) ?? next.buttonSeat,
        communityCards: [],
        awards: [],
        revealedHands: [],
      }
      break
    case 'PLAYER_ACTION': {
      const paid = numberValue(data.paid) ?? 0
      next = {
        ...next,
        currentBet: numberValue(data.currentBet) ?? next.currentBet,
        currentActorSeat: null,
        actionOptions: noActions,
        pot: next.pot + paid,
        players: updatePlayer(next.players, event.playerId, (player) => ({
          ...player,
          stack: numberValue(data.stack) ?? player.stack,
          streetBet: numberValue(data.streetBet) ?? player.streetBet,
          totalContribution: player.totalContribution + paid,
          status: statusValue(data.status) ?? player.status,
          canAct: data.canAct === true,
        })),
      }
      break
    }
    case 'PHASE_CHANGED': {
      const phase = phaseValue(data.to) ?? next.phase
      const newStreet = phase === 'FLOP' || phase === 'TURN' || phase === 'RIVER'
      next = {
        ...next,
        phase,
        currentActorSeat: null,
        actionOptions: noActions,
        players: newStreet
          ? next.players.map((player) => ({ ...player, streetBet: 0 }))
          : next.players,
      }
      break
    }
    case 'COMMUNITY_CARD_UPDATED':
      if (Array.isArray(data.communityCards)) {
        next = { ...next, communityCards: data.communityCards as GameSnapshot['communityCards'] }
      }
      break
    case 'TURN_CHANGED':
      next = {
        ...next,
        currentActorSeat: numberValue(data.seat) ?? null,
        turnId: numberValue(data.turnId) ?? next.turnId,
        currentBet: numberValue(data.currentBet) ?? next.currentBet,
        minimumRaise: numberValue(data.minimumRaise) ?? next.minimumRaise,
        actionOptions: noActions,
      }
      break
    case 'PLAYER_DISCONNECTED':
      next = {
        ...next,
        players: updatePlayer(next.players, event.playerId, (player) => ({
          ...player,
          status: 'DISCONNECTED',
          canAct: false,
        })),
      }
      break
    case 'PLAYER_RECONNECTED':
      next = {
        ...next,
        players: updatePlayer(next.players, event.playerId, (player) => ({
          ...player,
          status: statusValue(data.status) ?? player.status,
          canAct: false,
        })),
      }
      break
    case 'PLAYER_BUSTED':
      next = {
        ...next,
        players: updatePlayer(next.players, event.playerId, (player) => ({
          ...player,
          status: 'BUSTED',
          canAct: false,
          inHand: false,
        })),
      }
      break
    case 'SHOWDOWN': {
      const revealedHands = revealedHandsValue(data.revealedHands)
      if (revealedHands) next = { ...next, revealedHands }
      break
    }
    case 'SETTLEMENT': {
      const revealedHands = revealedHandsValue(data.revealedHands)
      next = {
        ...next,
        awards: Array.isArray(data.awards) ? data.awards as PotAward[] : next.awards,
        revealedHands: revealedHands ?? next.revealedHands,
      }
      break
    }
    case 'HAND_ENDED':
      next = {
        ...next,
        phase: 'ROUND_END',
        currentActorSeat: null,
        actionOptions: noActions,
        revealedHands: revealedHandsValue(data.revealedHands) ?? next.revealedHands,
      }
      break
    default:
      break
  }

  return next
}

const initialState = {
  connection: 'idle' as ConnectionState,
  snapshot: null,
  lastError: null,
  notice: null,
}

export const useGameStore = create<GameState>((set, get) => ({
  ...initialState,
  setConnection: (connection) => set({ connection }),
  replaceSnapshot: (snapshot) => set({
    snapshot: { ...snapshot, revealedHands: snapshot.revealedHands ?? [] },
    lastError: null,
  }),
  applyEvent: (event) => {
    const snapshot = get().snapshot
    if (!snapshot) return 'no-snapshot'
    if (event.sequence <= snapshot.lastSequence) return 'duplicate'
    if (event.sequence !== snapshot.lastSequence + 1) return 'gap'
    set({ snapshot: reduceGameEvent(snapshot, event) })
    return 'applied'
  },
  setError: (lastError) => set({ lastError }),
  setNotice: (notice) => set({ notice }),
  reset: () => set(initialState),
}))
