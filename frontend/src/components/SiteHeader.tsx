import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { AccountDock } from './AccountDock'
import { useAccountStore } from '../store/accountStore'
import { getPlayerName, savePlayerName } from '../utils/identity'

const navigation = [
  { to: '/play', label: 'PLAY' },
  { to: '/leaderboard', label: 'LEADERBOARD' },
  { to: '/store', label: 'STORE' },
]

export function SiteHeader() {
  const [name, setName] = useState(getPlayerName())
  const { mode, profile } = useAccountStore()

  function updateGuestName(value: string) {
    setName(value)
    if (value.trim()) savePlayerName(value)
  }

  return (
    <header className="site-header">
      <nav className="site-nav" aria-label="主导航">
        {navigation.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => isActive ? 'is-active' : ''}>
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="site-account">
        {mode === 'authenticated' && profile ? <AccountDock /> : (
          <label className="guest-name-field">
            <span>昵称</span>
            <input id="guest-player-name" name="guestPlayerName" autoComplete="nickname" value={name} maxLength={32} placeholder="输入昵称" onChange={(event) => updateGuestName(event.target.value)} />
          </label>
        )}
      </div>
    </header>
  )
}
