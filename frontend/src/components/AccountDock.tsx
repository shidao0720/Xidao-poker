import { Link } from 'react-router-dom'
import { useState } from 'react'
import { useAccountStore } from '../store/accountStore'
import { AvatarView } from './AvatarView'

export function AccountDock() {
  const { profile, checkIn, error, clearError } = useAccountStore()
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!profile) return null

  async function dailyCheckIn() {
    setBusy(true)
    try {
      const awarded = await checkIn()
      setNotice(awarded ? '签到成功，获得 500 筹码' : '今天已经签到过了')
    } catch {
      // The account store exposes the server-safe error message.
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="account-dock">
        <Link className="account-name" to="/profile" aria-label="打开个人主页">
          <AvatarView className="account-avatar" avatarKey={profile.avatarKey} name={profile.gameId} />
          <span className="account-status"><i /> ONLINE · LAN</span>
          <strong>{profile.gameId}</strong>
        </Link>
        <div className="currency-pill chip-currency"><small>筹码</small><b>{profile.wallet.chips.toLocaleString()}</b></div>
        <div className="currency-pill crystal-currency"><small>英魂结晶</small><b>{profile.wallet.spiritCrystals.toLocaleString()}</b></div>
        <button className="check-in-button" onClick={() => void dailyCheckIn()} disabled={busy}>签到</button>
      </div>
      {(notice || error) && <div className="account-notice">{error ?? notice}<button onClick={() => { setNotice(null); clearError() }}>×</button></div>}
    </>
  )
}
