import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'
import { AvatarView, AVATAR_OPTIONS, type AvatarKey } from '../components/AvatarView'
import { useAccountStore } from '../store/accountStore'

export function ProfilePage() {
  const navigate = useNavigate()
  const { profile, capabilities, exchange, updateAvatar, logout, error, clearError } = useAccountStore()
  const [chips, setChips] = useState(100)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [avatarBusy, setAvatarBusy] = useState(false)

  if (!profile) return null
  const currentAvatarKey = profile.avatarKey

  async function convert() {
    setBusy(true)
    try {
      await exchange(chips)
      setNotice(`已将 ${chips.toLocaleString()} 筹码凝结为 ${(chips / 10).toLocaleString()} 英魂结晶`)
    } catch {
      // The account store exposes the server-safe error message.
    } finally {
      setBusy(false)
    }
  }

  async function signOut() {
    await logout()
    navigate('/play', { replace: true })
  }

  async function chooseAvatar(nextAvatar: AvatarKey) {
    if (nextAvatar === currentAvatarKey) return
    setAvatarBusy(true)
    try {
      await updateAvatar(nextAvatar)
      setNotice('头像已更新')
    } catch {
      // The account store exposes the server-safe error message.
    } finally {
      setAvatarBusy(false)
    }
  }

  return (
    <main className="portal-page profile-page">
      <LobbySignalField />
      <SiteHeader />
      <section className="portal-content">
        <span className="portal-code">MASTER PROFILE / IDENTIFICATION</span>
        <h1>{profile.gameId}</h1>
        <div className="profile-grid">
          <section className="profile-panel profile-identity">
            <span className="profile-label">REGISTERED GAME IDS</span>
            <div className="profile-avatar-preview">
              <AvatarView className="profile-avatar" avatarKey={profile.avatarKey} name={profile.gameId} />
              <div><strong>{profile.gameId}</strong><span>当前头像</span></div>
            </div>
            <div className="avatar-picker" aria-label="选择头像">
              {AVATAR_OPTIONS.map((option) => (
                <button
                  key={option.key}
                  className={profile.avatarKey === option.key ? 'is-selected' : ''}
                  aria-label={option.label}
                  title={option.label}
                  disabled={avatarBusy}
                  onClick={() => void chooseAvatar(option.key)}
                >
                  <AvatarView avatarKey={option.key} name={option.label} />
                </button>
              ))}
            </div>
            <div className="game-id-list">{profile.gameIds.map((gameId) => <span key={gameId}>{gameId}</span>)}</div>
            <button className="profile-logout" onClick={() => void signOut()}>退出登录</button>
          </section>
          <section className="profile-panel wallet-panel">
            <span className="profile-label">WALLET RESONANCE</span>
            <div className="wallet-balance"><small>筹码</small><strong>{profile.wallet.chips.toLocaleString()}</strong></div>
            <div className="wallet-balance crystal-balance"><small>英魂结晶</small><strong>{profile.wallet.spiritCrystals.toLocaleString()}</strong></div>
          </section>
          <section className="profile-panel exchange-panel">
            <span className="profile-label">SPIRIT CORE CONDENSATION</span>
            <h2>英魂凝结</h2>
            <p>{capabilities?.chipsPerSpiritCrystal ?? 10} 筹码 = 1 英魂结晶</p>
            <label className="exchange-input"><span>投入筹码</span><input id="profile-exchange-chips" name="profileExchangeChips" type="number" min={10} step={10} max={profile.wallet.chips} value={chips} onChange={(event) => setChips(Number(event.target.value))} /></label>
            <div className="exchange-preview"><span>{chips.toLocaleString()} 筹码</span><b>→</b><strong>{Math.floor(chips / 10).toLocaleString()} 英魂结晶</strong></div>
            <button className="identity-submit" disabled={busy || chips <= 0 || chips % 10 !== 0 || chips > profile.wallet.chips} onClick={() => void convert()}>确认凝结（不可逆）</button>
          </section>
        </div>
        {(notice || error) && <div className="profile-notice">{error ?? notice}<button onClick={() => { setNotice(null); clearError() }}>×</button></div>}
      </section>
    </main>
  )
}
