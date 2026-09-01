import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { accountApi, type BroadcastMailInput, type MailInbox, type MailItem, type MailType } from '../api/account'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'
import { useAccountStore } from '../store/accountStore'
import { createRandomId } from '../utils/randomId'

const typeLabels: Record<MailType, string> = {
  ANNOUNCEMENT: '公告', NOTICE: '通知', REWARD: '奖励',
}
const emptyComposer: BroadcastMailInput = {
  type: 'ANNOUNCEMENT', subject: '', body: '', rewardChips: 0, rewardCrystals: 0, rewardSkinKey: '',
}

function hasAttachment(mail: MailItem) {
  return mail.rewardChips > 0 || mail.rewardCrystals > 0 || Boolean(mail.rewardSkinKey)
}

export function MailPage() {
  const { profile, bootstrap } = useAccountStore()
  const [inbox, setInbox] = useState<MailInbox>({ messages: [], unreadCount: 0 })
  const [selected, setSelected] = useState<MailItem | null>(null)
  const [composer, setComposer] = useState(emptyComposer)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  const load = useCallback(async () => {
    try { setInbox(await accountApi.inbox()); setError(null) }
    catch (caught) { setError(caught instanceof Error ? caught.message : '无法读取邮件') }
  }, [])

  useEffect(() => { void load() }, [load])
  if (!profile) return null

  async function open(mail: MailItem) {
    setSelected({ ...mail, read: true })
    if (!mail.read) {
      await accountApi.markMailRead(mail.mailId).catch(() => undefined)
      void load()
    }
  }

  async function claim(mail: MailItem) {
    setBusyId(mail.mailId)
    try {
      const result = await accountApi.claimMail(mail.mailId, createRandomId('mailclaim_'))
      setSelected(result.mail)
      setNotice('附件已领取并存入账户')
      await Promise.all([load(), bootstrap()])
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '附件领取失败')
    } finally { setBusyId(null) }
  }

  async function broadcast(event: FormEvent) {
    event.preventDefault()
    setSending(true)
    try {
      await accountApi.broadcastMail(createRandomId('broadcast_'), composer)
      setComposer(emptyComposer)
      setNotice('邮件已发送给所有现有玩家')
      await load()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '群发失败')
    } finally { setSending(false) }
  }

  return <main className="portal-page mail-page">
    <LobbySignalField /><SiteHeader />
    <section className="portal-content mail-content">
      <span className="portal-code">CHALDEA MESSAGE TERMINAL</span>
      <h1>Mail</h1>
      <p className="portal-lead">{inbox.unreadCount} 封未读</p>
      {(error || notice) && <div className="profile-notice">{error ?? notice}<button onClick={() => { setError(null); setNotice(null) }}>×</button></div>}
      <div className="mail-layout">
        <aside className="mail-list">
          {inbox.messages.length === 0 && <div className="mail-empty">暂无邮件</div>}
          {inbox.messages.map((mail) => <button key={mail.mailId} className={`${mail.read ? '' : 'is-unread'}${selected?.mailId === mail.mailId ? ' is-selected' : ''}`} onClick={() => void open(mail)}>
            <span>{typeLabels[mail.type]}</span><strong>{mail.subject}</strong>
            <small>{new Date(mail.createdAt).toLocaleString()}</small>
          </button>)}
        </aside>
        <section className="mail-reader">
          {!selected ? <div className="mail-empty"><b>✉</b><span>选择一封邮件</span></div> : <>
            <span className="mail-type">{typeLabels[selected.type]}</span>
            <h2>{selected.subject}</h2>
            <time>{new Date(selected.createdAt).toLocaleString()}</time>
            <p>{selected.body}</p>
            {hasAttachment(selected) && <div className="mail-attachment">
              <strong>附件</strong>
              <div>{selected.rewardChips > 0 && <span>筹码 × {selected.rewardChips.toLocaleString()}</span>}{selected.rewardCrystals > 0 && <span>英魂结晶 × {selected.rewardCrystals.toLocaleString()}</span>}{selected.rewardSkinKey && <span>皮肤 · {selected.rewardSkinKey}</span>}</div>
              <button className="identity-submit" disabled={selected.claimed || busyId === selected.mailId} onClick={() => void claim(selected)}>{selected.claimed ? '已领取' : busyId === selected.mailId ? '领取中…' : '领取附件'}</button>
            </div>}
          </>}
        </section>
      </div>
      {profile.admin && <section className="admin-mail-panel">
        <span className="profile-label">ADMINISTRATOR BROADCAST</span><h2>向全体玩家发送邮件</h2>
        <form onSubmit={(event) => void broadcast(event)}>
          <label><span>类型</span><select value={composer.type} onChange={(event) => setComposer({ ...composer, type: event.target.value as MailType })}><option value="ANNOUNCEMENT">公告</option><option value="NOTICE">通知</option><option value="REWARD">奖励</option></select></label>
          <label><span>标题</span><input required maxLength={80} value={composer.subject} onChange={(event) => setComposer({ ...composer, subject: event.target.value })} /></label>
          <label className="mail-body-field"><span>正文</span><textarea required maxLength={2000} value={composer.body} onChange={(event) => setComposer({ ...composer, body: event.target.value })} /></label>
          {composer.type === 'REWARD' && <div className="mail-reward-fields"><label><span>筹码</span><input type="number" min={0} max={10000000} value={composer.rewardChips} onChange={(event) => setComposer({ ...composer, rewardChips: Number(event.target.value) })} /></label><label><span>英魂结晶</span><input type="number" min={0} max={1000000} value={composer.rewardCrystals} onChange={(event) => setComposer({ ...composer, rewardCrystals: Number(event.target.value) })} /></label><label><span>皮肤 Key</span><input maxLength={64} placeholder="例如 saber-blue" value={composer.rewardSkinKey} onChange={(event) => setComposer({ ...composer, rewardSkinKey: event.target.value })} /></label></div>}
          <button className="identity-submit" disabled={sending}>{sending ? '发送中…' : '发送给所有玩家'}</button>
        </form>
      </section>}
    </section>
  </main>
}
