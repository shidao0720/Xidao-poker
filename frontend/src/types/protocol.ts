export type GamePhase =
  | 'WAITING'
  | 'READY'
  | 'DEALING'
  | 'PREFLOP'
  | 'FLOP'
  | 'TURN'
  | 'RIVER'
  | 'SHOWDOWN'
  | 'SETTLEMENT'
  | 'ROUND_END'

export type PlayerStatus =
  | 'ACTIVE'
  | 'FOLDED'
  | 'ALL_IN'
  | 'DISCONNECTED'
  | 'SPECTATOR'
  | 'BUSTED'

export type PlayerConnectionStatus = 'CONNECTED' | 'DISCONNECTED'
export type PlayerSeatStatus = 'SEATED' | 'SPECTATOR' | 'BUSTED'
export type PlayerHandStatus = 'NOT_IN_HAND' | 'ACTIVE' | 'FOLDED' | 'ALL_IN'

export type ActionType = 'FOLD' | 'CHECK' | 'CALL' | 'BET' | 'RAISE' | 'ALL_IN'
export type HandCategory =
  | 'HIGH_CARD'
  | 'ONE_PAIR'
  | 'TWO_PAIR'
  | 'THREE_OF_A_KIND'
  | 'STRAIGHT'
  | 'FLUSH'
  | 'FULL_HOUSE'
  | 'FOUR_OF_A_KIND'
  | 'STRAIGHT_FLUSH'
export type Rank =
  | 'TWO'
  | 'THREE'
  | 'FOUR'
  | 'FIVE'
  | 'SIX'
  | 'SEVEN'
  | 'EIGHT'
  | 'NINE'
  | 'TEN'
  | 'JACK'
  | 'QUEEN'
  | 'KING'
  | 'ACE'
export type Suit = 'SPADES' | 'HEARTS' | 'DIAMONDS' | 'CLUBS'

export interface Card {
  rank: Rank
  suit: Suit
}

export interface Pot {
  amount: number
  eligiblePlayerIds: string[]
}

export interface PotAward {
  potAmount: number
  winnings: Record<string, number>
}

export interface RevealedHandSnapshot {
  playerId: string
  showdown: boolean
  category: HandCategory | null
  holeCards: Card[]
  bestCards: Card[]
}

export interface PlayerSnapshot {
  id: string
  name: string
  seat: number
  stack: number
  streetBet: number
  totalContribution: number
  status: PlayerStatus
  connectionStatus: PlayerConnectionStatus
  seatStatus: PlayerSeatStatus
  handStatus: PlayerHandStatus
  inHand: boolean
  canAct: boolean
  ready: boolean
  holeCards: Card[]
  avatarKey?: string
}

export interface ActionOptions {
  legalActions: ActionType[]
  toCall: number
  callAmount: number
  minimumBetTo: number | null
  minimumRaiseTo: number | null
  maximumTo: number
}

export interface GameSnapshot {
  sessionId: string
  ownerId: string | null
  handId: number
  phase: GamePhase
  buttonSeat: number | null
  smallBlindSeat: number | null
  bigBlindSeat: number | null
  currentActorSeat: number | null
  turnId: number
  currentBet: number
  minimumRaise: number
  pot: number
  pots: Pot[]
  communityCards: Card[]
  players: PlayerSnapshot[]
  actionOptions: ActionOptions
  awards: PotAward[]
  revealedHands: RevealedHandSnapshot[]
  lastSequence: number
}

export interface RoomSummary {
  roomId: string
  roomName: string
  createdAt: string
  phase: GamePhase
  playerCount: number
  connectedCount: number
  smallBlind: number
  bigBlind: number
  buyIn: number
  maxPlayers: number
}

export interface CreateRoomInput {
  roomName: string
  smallBlind: number
  bigBlind: number
  buyIn: number
  maxPlayers: number
}

export interface GameEvent {
  sequence: number
  type: GameEventType
  handId: number
  playerId: string | null
  data: Record<string, unknown>
}

export type GameEventType =
  | 'PLAYER_JOINED'
  | 'PLAYER_LEFT'
  | 'READY_CHANGED'
  | 'OWNER_CHANGED'
  | 'GAME_STARTED'
  | 'HAND_STARTED'
  | 'BLINDS_POSTED'
  | 'HOLE_CARDS_DEALT'
  | 'TURN_CHANGED'
  | 'PLAYER_ACTION'
  | 'PHASE_CHANGED'
  | 'COMMUNITY_CARD_UPDATED'
  | 'PLAYER_DISCONNECTED'
  | 'PLAYER_RECONNECTED'
  | 'SHOWDOWN'
  | 'SETTLEMENT'
  | 'PLAYER_BUSTED'
  | 'HAND_ENDED'

export interface ServerEnvelope<T = unknown> {
  protocolVersion: number
  buildVersion: string
  type: GameEventType | ServerMessageType
  roomId: string | null
  requestId: string | null
  sequence: number | null
  handId: number | null
  connectionEpoch: number | null
  payload: T
}

export type ServerMessageType =
  | 'CONNECTION_READY'
  | 'COMMAND_RESULT'
  | 'ROOM_SNAPSHOT'
  | 'EVENT_REPLAY'
  | 'ERROR'
  | 'PONG'

export interface SnapshotPayload {
  connectionEpoch: number
  resumeToken: string
  state: GameSnapshot
}

export interface ConnectionReadyPayload {
  playerId: string
  connectionEpoch: number
  resumeToken: string
}

export interface ErrorPayload {
  code: string
  message: string
}

export interface EventReplayPayload {
  snapshotRequired: boolean
  oldestAvailableSequence: number
  latestSequence: number
  events: GameEvent[]
}

export interface ClientEnvelope {
  type:
    | 'READY'
    | 'START_GAME'
    | 'PLAYER_ACTION'
    | 'REQUEST_SNAPSHOT'
    | 'REPLAY_EVENTS'
    | 'LEAVE'
    | 'PING'
  requestId: string
  payload: Record<string, unknown>
}

export type ConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'closed'
