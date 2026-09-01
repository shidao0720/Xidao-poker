import type { CosmeticLoadout, CosmeticSlot } from '../api/account'

export const COSMETIC_LABELS: Record<string, string> = {
  'observer-frame': '世界线观测者',
  'grail-frame': '月海圣杯',
  'void-frame': '虚空王冠',
  'red-lance': '赤枪斜切',
  'time-machine': '时间机器 C204',
  'moon-grail': '月蚀圣杯',
  'chosen-observer': '被选中的观测者',
  'king-heroes': '英雄王',
  'spirit-pulse': '灵子脉冲',
  'grail-judgement': '圣杯裁决',
  'command-spell': '令咒宣告',
  'worldline-collapse': '世界线崩解',
  avalon: '遥远的理想乡',
}

const SAFE_KEYS = new Set(Object.keys(COSMETIC_LABELS))

export function cosmeticClass(prefix: string, key: string | null | undefined): string {
  return key && SAFE_KEYS.has(key) ? `${prefix}-${key}` : ''
}

export function cosmeticLabel(key: string | null | undefined): string | null {
  return key ? COSMETIC_LABELS[key] ?? null : null
}

export function loadoutValue(loadout: CosmeticLoadout, slot: CosmeticSlot): string | null {
  switch (slot) {
    case 'AVATAR_FRAME': return loadout.avatarFrame
    case 'CARD_BACK': return loadout.cardBack
    case 'TITLE': return loadout.title
    case 'BUTTON_EFFECT': return loadout.buttonEffect
    case 'VICTORY_EFFECT': return loadout.victoryEffect
    case 'PROFILE_STYLE': return loadout.profileStyle
  }
}
