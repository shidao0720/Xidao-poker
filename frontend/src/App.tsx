import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { LobbyPage } from './pages/LobbyPage'
import { TablePage } from './pages/TablePage'
import { LoginPage } from './pages/LoginPage'
import { LeaderboardPage } from './pages/LeaderboardPage'
import { StorePage } from './pages/StorePage'
import { ProfilePage } from './pages/ProfilePage'
import { useAccountStore } from './store/accountStore'

export default function App() {
  const { mode, bootstrap } = useAccountStore()

  useEffect(() => { void bootstrap() }, [bootstrap])

  if (mode === 'loading') return <main className="bootstrap-shell"><div className="chip-loader"><span /><span /><span /></div></main>
  if (mode === 'required') return <LoginPage />

  return (
    <Routes>
      <Route path="/" element={<Navigate to="/play" replace />} />
      <Route path="/play" element={<LobbyPage />} />
      <Route path="/leaderboard" element={<LeaderboardPage />} />
      <Route path="/store" element={<StorePage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/rooms/:roomId" element={<TablePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
