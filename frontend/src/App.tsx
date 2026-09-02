import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { LobbyPage } from './pages/LobbyPage'
import { TablePage } from './pages/TablePage'
import { LoginPage } from './pages/LoginPage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { StorePage } from './pages/StorePage'
import { ProfilePage } from './pages/ProfilePage'
import { MailPage } from './pages/MailPage'
import { ArenaLobbyPage } from './pages/ArenaLobbyPage'
import { ArenaGamePage } from './pages/ArenaGamePage'
import { AdminPage } from './pages/AdminPage'
import { FriendsPage } from './pages/FriendsPage'
import { accountApi } from './api/account'
import { createRandomId } from './utils/randomId'
import { BackgroundMusic } from './components/LobbyMusic'
import { useAccountStore } from './store/accountStore'

export default function App() {
  const { mode, bootstrap } = useAccountStore()
  const location = useLocation()

  useEffect(() => { void bootstrap() }, [bootstrap])

  useEffect(() => {
    if (mode !== 'authenticated') return
    const beat = () => void accountApi.heartbeat(createRandomId('presence_')).catch(() => undefined)
    beat()
    const timer = window.setInterval(beat, 20_000)
    return () => window.clearInterval(timer)
  }, [mode])

  const music = <BackgroundMusic paused={location.pathname.startsWith('/rooms/') || location.pathname.startsWith('/arena/')} />
  if (mode === 'loading') return <>{music}<main className="bootstrap-shell"><div className="chip-loader"><span /><span /><span /></div></main></>
  if (mode === 'required') return <>{music}<LoginPage /></>

  return (
    <>{music}<Routes>
      <Route path="/" element={<Navigate to="/play" replace />} />
      <Route path="/play" element={<LobbyPage />} />
      <Route path="/leaderboard" element={<LeaderboardPage />} />
      <Route path="/store" element={<StorePage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/mail" element={<MailPage />} />
      <Route path="/arena" element={<ArenaLobbyPage />} />
      <Route path="/arena/:roomId" element={<ArenaGamePage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route path="/friends" element={<FriendsPage />} />
      <Route path="/rooms/:roomId" element={<TablePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes></>
  )
}
