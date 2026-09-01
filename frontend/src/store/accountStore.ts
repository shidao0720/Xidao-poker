import { create } from 'zustand'
import { accountApi, type AccountProfile, type CosmeticSlot, type IdentityInput, type RedemptionResult, type RuntimeCapabilities, type StorePurchaseResult } from '../api/account'
import { applyAccountIdentity } from '../utils/identity'
import { createRandomId } from '../utils/randomId'
import type { AvatarKey } from '../components/AvatarView'

type AccountMode = 'loading' | 'guest' | 'required' | 'authenticated'

interface AccountState {
  mode: AccountMode
  capabilities: RuntimeCapabilities | null
  profile: AccountProfile | null
  error: string | null
  bootstrap: () => Promise<void>
  authenticate: (kind: 'login' | 'register', input: IdentityInput) => Promise<void>
  logout: () => Promise<void>
  checkIn: () => Promise<boolean>
  exchange: (chips: number) => Promise<void>
  updateAvatar: (avatarKey: AvatarKey) => Promise<void>
  redeem: (code: string) => Promise<RedemptionResult>
  purchaseStoreItem: (itemKey: string) => Promise<StorePurchaseResult>
  equipCosmetic: (slot: CosmeticSlot, itemKey: string | null) => Promise<void>
  clearError: () => void
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : '请求未能完成'
}

export const useAccountStore = create<AccountState>((set, get) => ({
  mode: 'loading',
  capabilities: null,
  profile: null,
  error: null,

  bootstrap: async () => {
    try {
      const capabilities = await accountApi.runtime()
      if (!capabilities.accountsEnabled) {
        set({ mode: 'guest', capabilities, profile: null, error: null })
        return
      }
      try {
        const profile = await accountApi.me()
        applyAccountIdentity(profile.gameId)
        set({ mode: 'authenticated', capabilities, profile, error: null })
      } catch (error) {
        const status = (error as Error & { status?: number }).status
        if (status === 401) set({ mode: 'required', capabilities, profile: null, error: null })
        else throw error
      }
    } catch (error) {
      set({ mode: 'required', error: message(error) })
    }
  },

  authenticate: async (kind, input) => {
    set({ error: null })
    try {
      const result = kind === 'login' ? await accountApi.login(input) : await accountApi.register(input)
      applyAccountIdentity(result.profile.gameId)
      set({ mode: 'authenticated', profile: result.profile, error: null })
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  logout: async () => {
    await accountApi.logout().catch(() => undefined)
    set({ mode: 'required', profile: null, error: null })
  },

  checkIn: async () => {
    try {
      const result = await accountApi.checkIn(createRandomId('checkin_'))
      const profile = get().profile
      if (profile) set({ profile: { ...profile, wallet: result.wallet }, error: null })
      return result.awarded
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  exchange: async (chips) => {
    try {
      const wallet = await accountApi.exchange(createRandomId('exchange_'), chips)
      const profile = get().profile
      if (profile) set({ profile: { ...profile, wallet }, error: null })
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  updateAvatar: async (avatarKey) => {
    try {
      const profile = await accountApi.updateAvatar(avatarKey)
      applyAccountIdentity(profile.gameId)
      set({ profile, error: null })
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  redeem: async (code) => {
    try {
      const result = await accountApi.redeem(createRandomId('redeem_'), code)
      const profile = get().profile
      if (profile) set({ profile: { ...profile, wallet: result.wallet }, error: null })
      return result
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  purchaseStoreItem: async (itemKey) => {
    try {
      const result = await accountApi.purchaseStoreItem(createRandomId('store_'), itemKey)
      const profile = get().profile
      if (profile) {
        set({ profile: { ...profile, wallet: result.wallet, cosmetics: result.cosmetics, loadout: result.loadout }, error: null })
      }
      return result
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  equipCosmetic: async (slot, itemKey) => {
    try {
      const profile = await accountApi.equipCosmetic(slot, createRandomId('equip_'), itemKey)
      set({ profile, error: null })
    } catch (error) {
      set({ error: message(error) })
      throw error
    }
  },

  clearError: () => set({ error: null }),
}))
