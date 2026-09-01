import { Link } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAccountStore } from '../store/accountStore'
import { AvatarView } from './AvatarView'
import { accountApi } from '../api/account'
import { cosmeticClass, cosmeticLabel } from '../utils/cosmetics'

export function AccountDock() {
  const { profile, checkIn, error, clearError } = useAccountStore()
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [unreadMail, setUnreadMail] = useState(0)

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const inbox = await accountApi.inbox()
        if (!cancelled) setUnreadMail(inbox.unreadCount)
      } catch { /* Account errors remain handled by authenticated pages. */ }
    }
    void load()
    const timer = window.setInterval(() => void load(), 15_000)
    return () => { cancelled = true; window.clearInterval(timer) }
  }, [])

  if (!profile) return null
  const title = cosmeticLabel(profile.loadout.title)

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
        <Link className="mail-dock-button" to="/mail" aria-label={`邮件中心，${unreadMail} 封未读邮件`}>
          <span aria-hidden="true">✉</span>{unreadMail > 0 && <b>{unreadMail > 99 ? '99+' : unreadMail}</b>}
        </Link>
        <Link className="account-name" to="/profile" aria-label="打开个人主页">
          <span className={`account-avatar-cosmetic ${cosmeticClass('cosmetic-avatar-frame', profile.loadout.avatarFrame)}`}>
            <AvatarView className="account-avatar" avatarKey={profile.avatarKey} name={profile.gameId} />
          </span>
          <span className="account-status"><i /> ONLINE · LAN</span>
          <strong>{profile.gameId}</strong>
          {title && <small className={cosmeticClass('cosmetic-title', profile.loadout.title)}>{title}</small>}
        </Link>
        <div className="currency-pill chip-currency"><small>筹码</small><b>{profile.wallet.chips.toLocaleString()}</b></div>
        <div className="currency-pill crystal-currency"><small>英魂结晶</small><b>{profile.wallet.spiritCrystals.toLocaleString()}</b></div>
        <button className="check-in-button" onClick={() => void dailyCheckIn()} disabled={busy}>签到</button>
      </div>
      {(notice || error) && <div className="account-notice">{error ?? notice}<button onClick={() => { setNotice(null); clearError() }}>×</button></div>}
    </>
  )
}
