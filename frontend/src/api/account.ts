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
  admin: boolean
  cosmetics: string[]
  loadout: CosmeticLoadout
}

export type CosmeticSlot = 'AVATAR_FRAME' | 'CARD_BACK' | 'TITLE' | 'BUTTON_EFFECT' | 'VICTORY_EFFECT' | 'PROFILE_STYLE'
export interface CosmeticLoadout {
  avatarFrame: string | null
  cardBack: string | null
  title: string | null
  buttonEffect: string | null
  victoryEffect: string | null
  profileStyle: string | null
}

export type StoreCategory = CosmeticSlot | 'BUNDLE'
export type StoreRarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'
export interface StoreItem {
  key: string
  category: StoreCategory
  name: string
  subtitle: string
  series: string
  rarity: StoreRarity
  priceCrystals: number
  glyph: string
  color: string
  description: string
  tags: string[]
  features: string[]
  grants: string[]
}
export interface StorePurchaseResult {
  item: StoreItem
  wallet: WalletSnapshot
  cosmetics: string[]
  loadout: CosmeticLoadout
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

export interface RedemptionResult {
  currency: 'CHIP' | 'CRYSTAL'
  amount: number
  wallet: WalletSnapshot
}

export interface TableSessionResult {
  status: 'ACTIVE' | 'SETTLED'
  buyIn: number
  returnedChips: number | null
  netChips: number | null
}

export type MailType = 'ANNOUNCEMENT' | 'NOTICE' | 'REWARD'
export interface MailItem {
  mailId: string
  type: MailType
  subject: string
  body: string
  rewardChips: number
  rewardCrystals: number
  rewardSkinKey: string | null
  createdAt: string
  read: boolean
  claimed: boolean
}
export interface MailInbox { messages: MailItem[]; unreadCount: number }
export interface MailClaimResult { mail: MailItem; wallet: WalletSnapshot; cosmetics: string[] }
export interface BroadcastMailInput {
  type: MailType
  subject: string
  body: string
  rewardChips: number
  rewardCrystals: number
  rewardSkinKey: string
}

export interface AdminOverview {
  accounts: number
  administrators: number
  enabledRedemptionCodes: number
  redemptionClaims: number
  mailMessages: number
  deliveredMail: number
  grantedCosmetics: number
}

export interface AdminRedemptionCode {
  codeHash: string
  currency: 'CHIP' | 'CRYSTAL'
  rewardAmount: number
  maxRedemptions: number | null
  redeemedCount: number
  validFrom: string
  validUntil: string | null
  enabled: boolean
  createdAt: string
}

export interface CreateRedemptionCodeInput {
  code: string
  currency: 'CHIP' | 'CRYSTAL'
  rewardAmount: number
  maxRedemptions: number | null
  validFrom: string | null
  validUntil: string | null
}

export interface AdminRedemptionCodeCreated {
  code: string
  redemptionCode: AdminRedemptionCode
}

export interface FriendView {
  friendshipId: string
  gameId: string
  avatarKey: AvatarKey
  online: boolean
}
export interface FriendDashboard {
  friends: FriendView[]
  incomingRequests: FriendView[]
  outgoingRequests: FriendView[]
}

export interface AdminAccountView {
  accountId: string
  realName: string
  primaryGameId: string
  gameIds: string[]
  avatarKey: AvatarKey
  administrator: boolean
  wallet: WalletSnapshot
  online: boolean
  passwordStatus: 'BCRYPT_PROTECTED'
}
export interface AdminFriendshipView {
  friendshipId: string
  requesterAccountId: string
  requesterGameId: string
  addresseeAccountId: string
  addresseeGameId: string
  status: 'PENDING' | 'ACCEPTED'
  updatedAt: string
}
export interface AdminWalletAdjustment { accountId: string; wallet: WalletSnapshot }

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
  heartbeat: (requestId: string) => apiRequest<void>('/api/friends/heartbeat', {
    method: 'POST', body: JSON.stringify({ requestId }),
  }),
  checkIn: (requestId: string) => apiRequest<CheckInResult>('/api/account/check-in', {
    method: 'POST',
    body: JSON.stringify({ requestId }),
  }),
  exchange: (requestId: string, chips: number) => apiRequest<WalletSnapshot>('/api/wallet/exchange', {
    method: 'POST',
    body: JSON.stringify({ requestId, chips }),
  }),
  redeem: (requestId: string, code: string) => apiRequest<RedemptionResult>('/api/account/redeem', {
    method: 'POST',
    body: JSON.stringify({ requestId, code }),
  }),
  tableResult: (roomId: string) => apiRequest<TableSessionResult>(
    `/api/account/table-result?roomId=${encodeURIComponent(roomId)}`,
  ),
  inbox: () => apiRequest<MailInbox>('/api/account/mail'),
  markMailRead: (mailId: string) => apiRequest<MailItem>(`/api/account/mail/${encodeURIComponent(mailId)}/read`, { method: 'PUT' }),
  claimMail: (mailId: string, requestId: string) => apiRequest<MailClaimResult>(`/api/account/mail/${encodeURIComponent(mailId)}/claim`, {
    method: 'POST', body: JSON.stringify({ requestId }),
  }),
  broadcastMail: (requestId: string, input: BroadcastMailInput) => apiRequest<MailItem>('/api/admin/mail/broadcast', {
    method: 'POST', body: JSON.stringify({ ...input, rewardSkinKey: input.rewardSkinKey || null, requestId }),
  }),
  adminOverview: () => apiRequest<AdminOverview>('/api/admin/overview'),
  adminRedemptionCodes: () => apiRequest<AdminRedemptionCode[]>('/api/admin/redemption-codes'),
  createRedemptionCode: (requestId: string, input: CreateRedemptionCodeInput) => apiRequest<AdminRedemptionCodeCreated>('/api/admin/redemption-codes', {
    method: 'POST', body: JSON.stringify({ ...input, requestId }),
  }),
  setRedemptionCodeEnabled: (codeHash: string, requestId: string, enabled: boolean) => apiRequest<AdminRedemptionCode>(
    `/api/admin/redemption-codes/${encodeURIComponent(codeHash)}/enabled`,
    { method: 'PUT', body: JSON.stringify({ requestId, enabled }) },
  ),
  friends: () => apiRequest<FriendDashboard>('/api/friends'),
  sendFriendRequest: (gameId: string, requestId: string) => apiRequest<FriendDashboard>('/api/friends/requests', {
    method: 'POST', body: JSON.stringify({ gameId, requestId }),
  }),
  acceptFriendRequest: (friendshipId: string, requestId: string) => apiRequest<FriendDashboard>(`/api/friends/${encodeURIComponent(friendshipId)}/accept`, {
    method: 'POST', body: JSON.stringify({ requestId }),
  }),
  rejectFriendRequest: (friendshipId: string, requestId: string) => apiRequest<FriendDashboard>(`/api/friends/${encodeURIComponent(friendshipId)}/reject`, {
    method: 'POST', body: JSON.stringify({ requestId }),
  }),
  removeFriend: (friendshipId: string, requestId: string) => apiRequest<FriendDashboard>(`/api/friends/${encodeURIComponent(friendshipId)}/remove`, {
    method: 'POST', body: JSON.stringify({ requestId }),
  }),
  adminAccounts: () => apiRequest<AdminAccountView[]>('/api/admin/accounts'),
  adminFriendships: () => apiRequest<AdminFriendshipView[]>('/api/admin/friendships'),
  adjustAccountWallet: (accountId: string, requestId: string, chipDelta: number, crystalDelta: number, reason: string) => apiRequest<AdminWalletAdjustment>(`/api/admin/accounts/${encodeURIComponent(accountId)}/wallet-adjustments`, {
    method: 'POST', body: JSON.stringify({ requestId, chipDelta, crystalDelta, reason }),
  }),
  resetAccountPassword: (accountId: string, requestId: string, newPassword: string) => apiRequest<void>(`/api/admin/accounts/${encodeURIComponent(accountId)}/password-reset`, {
    method: 'POST', body: JSON.stringify({ requestId, newPassword }),
  }),
  createAdminFriendship: (firstAccountId: string, secondAccountId: string, requestId: string) => apiRequest<AdminFriendshipView>('/api/admin/friendships', {
    method: 'POST', body: JSON.stringify({ firstAccountId, secondAccountId, requestId }),
  }),
  removeAdminFriendship: (friendshipId: string, requestId: string) => apiRequest<void>(`/api/admin/friendships/${encodeURIComponent(friendshipId)}/remove`, {
    method: 'POST', body: JSON.stringify({ requestId }),
  }),
  storeCatalog: () => apiRequest<StoreItem[]>('/api/store/catalog'),
  purchaseStoreItem: (requestId: string, itemKey: string) => apiRequest<StorePurchaseResult>('/api/store/purchase', {
    method: 'POST', body: JSON.stringify({ requestId, itemKey }),
  }),
  equipCosmetic: (slot: CosmeticSlot, requestId: string, itemKey: string | null) => apiRequest<AccountProfile>(
    `/api/account/loadout/${encodeURIComponent(slot)}`,
    { method: 'PUT', body: JSON.stringify({ requestId, itemKey }) },
  ),
  leaderboards: () => apiRequest<Leaderboards>('/api/leaderboards'),
}
