import { useState } from 'react'

export type AvatarKey =
  | 'default' | 'azure' | 'crimson' | 'violet' | 'moon' | 'prism'
  | 'bobo' | 'god' | 'lian' | 'niu' | 'yun' | 'zhen' | 'zhi'
  | 'dna' | 'li' | 'lu' | 'wu' | 'zhan'

export const AVATAR_OPTIONS: Array<{ key: AvatarKey; label: string }> = [
  { key: 'default', label: '默认观测者' },
  { key: 'azure', label: '苍蓝回路' },
  { key: 'crimson', label: '赤色命运' },
  { key: 'violet', label: '紫苑灵基' },
  { key: 'moon', label: '月下观测' },
  { key: 'prism', label: '棱镜英魂' },
  { key: 'bobo', label: 'BOBO' },
  { key: 'god', label: 'GOD' },
  { key: 'lian', label: 'LIAN' },
  { key: 'niu', label: 'NIU' },
  { key: 'yun', label: 'YUN' },
  { key: 'zhen', label: 'ZHEN' },
  { key: 'zhi', label: 'ZHI' },
  { key: 'dna', label: 'DNA' },
  { key: 'li', label: 'LI' },
  { key: 'lu', label: 'LU' },
  { key: 'wu', label: 'WU' },
  { key: 'zhan', label: 'ZHAN' },
]

const avatarFiles: Partial<Record<AvatarKey, string>> = {
  bobo: 'bobo.svg',
  god: 'god.svg',
  lian: 'lian.svg',
  niu: 'niu.svg',
  yun: 'yun.svg',
  zhen: 'zhen.svg',
  zhi: 'zhi.svg',
  dna: 'dna.svg',
  li: 'li.svg',
  lu: 'lu.svg',
  wu: 'wu.svg',
  zhan: 'zhan.svg',
}

export function avatarKey(value: string | null | undefined): AvatarKey {
  return AVATAR_OPTIONS.some((option) => option.key === value) ? value as AvatarKey : 'default'
}

interface AvatarViewProps {
  avatarKey?: string | null | undefined
  name: string
  className?: string
}

export function AvatarView({ avatarKey: value, name, className = '' }: AvatarViewProps) {
  const [failed, setFailed] = useState(false)
  const key = avatarKey(value)
  const file = avatarFiles[key] ?? `${key}.svg`
  return failed ? (
    <span className={`${className} avatar-fallback`} aria-label={`${name}头像`}>
      {name.slice(0, 1).toUpperCase()}
    </span>
  ) : (
    <img
      className={`${className} avatar-image`}
      src={`/assets/images/avatars/${file}`}
      alt={`${name}头像`}
      onError={() => setFailed(true)}
    />
  )
}
