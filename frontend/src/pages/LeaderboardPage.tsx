import { useEffect, useState } from 'react'
import { accountApi, type Leaderboards } from '../api/account'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'

const rankingGroups = [
  ['胜利手数', 'mostHandsWon'],
  ['累计赢得筹码', 'mostTotalWinnings'],
  ['单手最高净收益', 'largestSingleHandGain'],
] as const

export function LeaderboardPage() {
  const [rankings, setRankings] = useState<Leaderboards | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void accountApi.leaderboards().then(setRankings).catch(() => setError('排行榜读取失败'))
  }, [])

  return (
    <main className="portal-page leaderboard-page">
      <LobbySignalField />
      <SiteHeader />
      <section className="portal-content">
        <span className="portal-code">THRONE OF HEROES / RECORDS</span>
        <h1>Leaderboard</h1>
        <p className="portal-lead">英灵榜</p>
        {error && <div className="alert" role="alert">{error}</div>}
        <div className="leaderboard-grid">
          {rankingGroups.map(([label, key], index) => (
            <section className="leaderboard-board" key={key}>
              <span className="board-index">0{index + 1}</span>
              <h2>{label}</h2>
              {rankings ? rankings[key].map((entry) => (
                <div className="leaderboard-entry" key={entry.gameId}>
                  <i>{String(entry.rank).padStart(2, '0')}</i>
                  <strong>{entry.gameId}</strong>
                  <b>{entry.value.toLocaleString()}</b>
                </div>
              )) : <div className="leaderboard-loading">同步中…</div>}
              {rankings && rankings[key].length === 0 && <div className="leaderboard-loading">等待第一位记录创造者</div>}
            </section>
          ))}
        </div>
      </section>
    </main>
  )
}
