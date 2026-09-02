import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { accountApi, type FriendDashboard, type FriendView } from '../api/account'
import { AvatarView } from '../components/AvatarView'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'
import { createRandomId } from '../utils/randomId'

const empty: FriendDashboard = { friends: [], incomingRequests: [], outgoingRequests: [] }

function FriendCard({ friend, actions }: { friend: FriendView; actions: ReactNode }) {
  return <article className="friend-card">
    <span className={`friend-avatar ${friend.online ? 'is-online' : ''}`}><AvatarView avatarKey={friend.avatarKey} name={friend.gameId} /></span>
    <div><strong>{friend.gameId}</strong><small><i />{friend.online ? 'ONLINE' : 'OFFLINE'}</small></div>
    <footer>{actions}</footer>
  </article>
}

export function FriendsPage() {
  const [dashboard, setDashboard] = useState(empty)
  const [gameId, setGameId] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try { setDashboard(await accountApi.friends()); setError(null) }
    catch (caught) { setError(caught instanceof Error ? caught.message : '好友列表读取失败') }
  }, [])

  useEffect(() => {
    void load()
    const timer = window.setInterval(() => void load(), 15_000)
    return () => window.clearInterval(timer)
  }, [load])

  async function add(event: FormEvent) {
    event.preventDefault(); setBusy('add'); setError(null)
    try {
      setDashboard(await accountApi.sendFriendRequest(gameId, createRandomId('friend_')))
      setGameId(''); setNotice('好友申请已发送')
    } catch (caught) { setError(caught instanceof Error ? caught.message : '好友申请发送失败') }
    finally { setBusy(null) }
  }

  async function mutate(friend: FriendView, action: 'accept' | 'reject' | 'remove') {
    setBusy(friend.friendshipId); setError(null)
    try {
      const requestId = createRandomId('friend_')
      const result = action === 'accept'
        ? await accountApi.acceptFriendRequest(friend.friendshipId, requestId)
        : action === 'reject'
          ? await accountApi.rejectFriendRequest(friend.friendshipId, requestId)
          : await accountApi.removeFriend(friend.friendshipId, requestId)
      setDashboard(result)
      setNotice(action === 'accept' ? '已成为好友' : action === 'reject' ? '已拒绝申请' : '已解除好友关系')
    } catch (caught) { setError(caught instanceof Error ? caught.message : '好友操作失败') }
    finally { setBusy(null) }
  }

  return <main className="portal-page friends-page">
    <LobbySignalField /><SiteHeader />
    <section className="portal-content friends-content">
      <span className="portal-code">WORLDLINE CONTACT NETWORK</span>
      <h1>Friends</h1>
      <p className="portal-lead">建立稳定世界线连接，查看好友当前在线状态。</p>
      {(error || notice) && <div className={`admin-notice ${error ? 'is-error' : ''}`}>{error ?? notice}<button onClick={() => { setError(null); setNotice(null) }}>×</button></div>}
      <form className="friend-search" onSubmit={(event) => void add(event)}>
        <label><span>GAME ID</span><input required maxLength={12} value={gameId} placeholder="输入对方游戏 ID" onChange={(event) => setGameId(event.target.value)} /></label>
        <button disabled={busy === 'add' || !gameId.trim()}>{busy === 'add' ? '发送中…' : '发送好友申请'}</button>
      </form>

      {dashboard.incomingRequests.length > 0 && <section className="friend-section"><header><span>INCOMING SIGNAL</span><h2>收到的申请</h2></header><div className="friend-grid">
        {dashboard.incomingRequests.map((friend) => <FriendCard key={friend.friendshipId} friend={friend} actions={<><button disabled={busy === friend.friendshipId} onClick={() => void mutate(friend, 'accept')}>接受</button><button className="is-danger" disabled={busy === friend.friendshipId} onClick={() => void mutate(friend, 'reject')}>拒绝</button></>} />)}
      </div></section>}

      <section className="friend-section"><header><span>ESTABLISHED LINKS</span><h2>我的好友 · {dashboard.friends.length}</h2></header>
        {dashboard.friends.length === 0 ? <div className="friend-empty">暂时没有已建立的好友连接</div> : <div className="friend-grid">
          {dashboard.friends.map((friend) => <FriendCard key={friend.friendshipId} friend={friend} actions={<button className="is-danger" disabled={busy === friend.friendshipId} onClick={() => void mutate(friend, 'remove')}>删除好友</button>} />)}
        </div>}
      </section>

      {dashboard.outgoingRequests.length > 0 && <section className="friend-section"><header><span>AWAITING RESPONSE</span><h2>等待回应</h2></header><div className="friend-grid is-muted">
        {dashboard.outgoingRequests.map((friend) => <FriendCard key={friend.friendshipId} friend={friend} actions={<span>申请中</span>} />)}
      </div></section>}
    </section>
  </main>
}
