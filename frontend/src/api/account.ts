import { apiRequest } from './client'
import type { AvatarKey } from '../components/AvatarView'

export interface WalletSnapshot {
  chips: number
  spiritCrystals: number
}

export interface AccountProfile {
  gameId: string
  gameIds: string[]
  wallet: WalletSnapshot
  avatarKey: AvatarKey
}

export interface RuntimeCapabilities {
  accountsEnabled: boolean
  chipsPerSpiritCrystal: number
  playMoneyOnly: boolean
}

export interface IdentityInput {
  realName: string
  gameId: string
  password: string
}

export interface AuthResponse {
  expiresAt: string
  profile: AccountProfile
}

export interface LeaderboardEntry {
  rank: number
  gameId: string
  value: number
}

export interface Leaderboards {
  mostHandsWon: LeaderboardEntry[]
  mostTotalWinnings: LeaderboardEntry[]
  largestSingleHandGain: LeaderboardEntry[]
}

export interface CheckInResult {
  awarded: boolean
  awardedChips: number
  date: string
  wallet: WalletSnapshot
}

export const accountApi = {
  runtime: () => apiRequest<RuntimeCapabilities>('/api/runtime'),
  me: () => apiRequest<AccountProfile>('/api/account/me'),
  updateAvatar: (avatarKey: AvatarKey) => apiRequest<AccountProfile>('/api/account/avatar', {
    method: 'PUT',
    body: JSON.stringify({ avatarKey }),
  }),
  login: (input: IdentityInput) => apiRequest<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  }),
  register: (input: IdentityInput) => apiRequest<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  }),
  logout: () => apiRequest<void>('/api/auth/logout', { method: 'POST' }),
  checkIn: (requestId: string) => apiRequest<CheckInResult>('/api/account/check-in', {
    method: 'POST',
    body: JSON.stringify({ requestId }),
  }),
  exchange: (requestId: string, chips: number) => apiRequest<WalletSnapshot>('/api/wallet/exchange', {
    method: 'POST',
    body: JSON.stringify({ requestId, chips }),
  }),
  leaderboards: () => apiRequest<Leaderboards>('/api/leaderboards'),
  shopItems: () => apiRequest<unknown[]>('/api/shop/items'),
}
