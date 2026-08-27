import { Navigate, Route, Routes } from 'react-router-dom'
import { LobbyPage } from './pages/LobbyPage'
import { TablePage } from './pages/TablePage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LobbyPage />} />
      <Route path="/rooms/:roomId" element={<TablePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
