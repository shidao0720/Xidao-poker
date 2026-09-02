import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { accountApi, type AdminAccountView, type AdminFriendshipView, type AdminOverview, type AdminRedemptionCode, type BroadcastMailInput, type MailType, type StoreItem } from '../api/account'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'
import { useAccountStore } from '../store/accountStore'
import { createRandomId } from '../utils/randomId'

const emptyMail: BroadcastMailInput = {
  type: 'ANNOUNCEMENT', subject: '', body: '', rewardChips: 0, rewardCrystals: 0, rewardSkinKey: '',
}
const emptyOverview: AdminOverview = {
  accounts: 0, administrators: 0, enabledRedemptionCodes: 0, redemptionClaims: 0,
  mailMessages: 0, deliveredMail: 0, grantedCosmetics: 0,
}

function asIso(value: string): string | null {
  return value ? new Date(value).toISOString() : null
}

export function AdminPage() {
  const profile = useAccountStore((state) => state.profile)
  const [overview, setOverview] = useState(emptyOverview)
  const [codes, setCodes] = useState<AdminRedemptionCode[]>([])
  const [catalog, setCatalog] = useState<StoreItem[]>([])
  const [accounts, setAccounts] = useState<AdminAccountView[]>([])
  const [friendships, setFriendships] = useState<AdminFriendshipView[]>([])
  const [mail, setMail] = useState(emptyMail)
  const [code, setCode] = useState({ value: '', currency: 'CRYSTAL' as 'CHIP' | 'CRYSTAL', amount: 100, limit: '', from: '', until: '' })
  const [busy, setBusy] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [walletEdit, setWalletEdit] = useState({ chips: '0', crystals: '0', reason: '' })
  const [passwordReset, setPasswordReset] = useState('')
  const [friendPair, setFriendPair] = useState({ first: '', second: '' })

  const load = useCallback(async () => {
    const [nextOverview, nextCodes, nextCatalog, nextAccounts, nextFriendships] = await Promise.all([
      accountApi.adminOverview(), accountApi.adminRedemptionCodes(), accountApi.storeCatalog(),
      accountApi.adminAccounts(), accountApi.adminFriendships(),
    ])
    setOverview(nextOverview)
    setCodes(nextCodes)
    setCatalog(nextCatalog)
    setAccounts(nextAccounts)
    setFriendships(nextFriendships)
    setSelectedAccountId((current) => current || nextAccounts[0]?.accountId || '')
    setFriendPair((current) => ({
      first: current.first || nextAccounts[0]?.accountId || '',
      second: current.second || nextAccounts[1]?.accountId || '',
    }))
  }, [])

  useEffect(() => {
    if (profile?.admin) void load().catch((caught) => setError(caught instanceof Error ? caught.message : '运营数据读取失败'))
  }, [load, profile?.admin])

  const rewardSkins = useMemo(() => catalog.filter((item) => item.category !== 'BUNDLE'), [catalog])

  if (!profile) return null
  if (!profile.admin) return <main className="portal-page admin-page"><LobbySignalField /><SiteHeader />
    <section className="portal-content admin-forbidden"><span>ACCESS DENIED</span><h1>权限不足</h1><p>该区域仅对管理员开放。</p></section>
  </main>

  async function createCode(event: FormEvent) {
    event.preventDefault()
    setBusy('code'); setError(null); setNotice(null)
    try {
      const result = await accountApi.createRedemptionCode(createRandomId('admincode_'), {
        code: code.value, currency: code.currency, rewardAmount: code.amount,
        maxRedemptions: code.limit ? Number(code.limit) : null,
        validFrom: asIso(code.from), validUntil: asIso(code.until),
      })
      setNotice(`兑换码 ${result.code} 已创建。明文仅在此处显示，请立即保存。`)
      setCode({ value: '', currency: 'CRYSTAL', amount: 100, limit: '', from: '', until: '' })
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '兑换码创建失败') }
    finally { setBusy(null) }
  }

  async function toggleCode(item: AdminRedemptionCode) {
    setBusy(item.codeHash); setError(null)
    try {
      await accountApi.setRedemptionCodeEnabled(item.codeHash, createRandomId('admincode_'), !item.enabled)
      setNotice(item.enabled ? '兑换码已停用' : '兑换码已启用')
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '兑换码状态更新失败') }
    finally { setBusy(null) }
  }

  async function broadcast(event: FormEvent) {
    event.preventDefault()
    setBusy('mail'); setError(null); setNotice(null)
    try {
      await accountApi.broadcastMail(createRandomId('broadcast_'), mail)
      setMail(emptyMail)
      setNotice('运营邮件已发送给所有现有玩家。')
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '运营邮件发送失败') }
    finally { setBusy(null) }
  }

  async function adjustWallet(event: FormEvent) {
    event.preventDefault(); setBusy('wallet'); setError(null); setNotice(null)
    try {
      await accountApi.adjustAccountWallet(selectedAccountId, createRandomId('adminwallet_'),
        Number(walletEdit.chips), Number(walletEdit.crystals), walletEdit.reason)
      setWalletEdit({ chips: '0', crystals: '0', reason: '' })
      setNotice('账户货币已调整，变更原因与流水已写入审计记录。')
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '货币调整失败') }
    finally { setBusy(null) }
  }

  async function resetPassword(event: FormEvent) {
    event.preventDefault(); setBusy('password'); setError(null); setNotice(null)
    try {
      await accountApi.resetAccountPassword(selectedAccountId, createRandomId('adminpass_'), passwordReset)
      setPasswordReset('')
      setNotice('密码已使用 BCrypt 重新设置，该账户的全部旧会话已注销。')
    } catch (caught) { setError(caught instanceof Error ? caught.message : '密码重置失败') }
    finally { setBusy(null) }
  }

  async function createFriendship(event: FormEvent) {
    event.preventDefault(); setBusy('friendship'); setError(null); setNotice(null)
    try {
      await accountApi.createAdminFriendship(friendPair.first, friendPair.second, createRandomId('adminfriend_'))
      setNotice('好友关系已建立或待处理申请已批准。')
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '好友关系建立失败') }
    finally { setBusy(null) }
  }

  async function removeFriendship(item: AdminFriendshipView) {
    setBusy(item.friendshipId); setError(null); setNotice(null)
    try {
      await accountApi.removeAdminFriendship(item.friendshipId, createRandomId('adminfriend_'))
      setNotice('好友关系已解除。')
      await load()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '好友关系解除失败') }
    finally { setBusy(null) }
  }

  return <main className="portal-page admin-page">
    <LobbySignalField /><SiteHeader />
    <section className="portal-content admin-content">
      <span className="portal-code">CHALDEA OPERATIONS TERMINAL</span>
      <h1>Operations</h1>
      <p className="portal-lead">管理员运营控制台 · 所有变更均由服务端校验并去重</p>
      {(error || notice) && <div className={`admin-notice ${error ? 'is-error' : ''}`}>{error ?? notice}<button onClick={() => { setError(null); setNotice(null) }}>×</button></div>}

      <section className="admin-metrics">
        {[
          ['注册身份', overview.accounts], ['管理员', overview.administrators],
          ['启用兑换码', overview.enabledRedemptionCodes], ['兑换次数', overview.redemptionClaims],
          ['运营邮件', overview.mailMessages], ['已投递邮件', overview.deliveredMail], ['已发放藏品', overview.grantedCosmetics],
        ].map(([label, value]) => <article key={label}><small>{label}</small><strong>{Number(value).toLocaleString()}</strong></article>)}
      </section>

      <div className="admin-grid">
        <section className="admin-panel">
          <header><span>01 / REDEMPTION</span><h2>创建兑换码</h2></header>
          <form className="admin-form" onSubmit={(event) => void createCode(event)}>
            <label className="is-wide"><span>兑换码</span><input required minLength={4} maxLength={64} pattern="[A-Za-z0-9_-]+" placeholder="FATE-2026" value={code.value} onChange={(event) => setCode({ ...code, value: event.target.value })} /></label>
            <label><span>奖励货币</span><select value={code.currency} onChange={(event) => setCode({ ...code, currency: event.target.value as 'CHIP' | 'CRYSTAL' })}><option value="CRYSTAL">英魂结晶</option><option value="CHIP">筹码</option></select></label>
            <label><span>奖励数量</span><input required type="number" min={1} max={10000000} value={code.amount} onChange={(event) => setCode({ ...code, amount: Number(event.target.value) })} /></label>
            <label><span>兑换上限（留空不限）</span><input type="number" min={1} max={1000000} value={code.limit} onChange={(event) => setCode({ ...code, limit: event.target.value })} /></label>
            <label><span>生效时间（留空立即）</span><input type="datetime-local" value={code.from} onChange={(event) => setCode({ ...code, from: event.target.value })} /></label>
            <label><span>失效时间（留空永久）</span><input type="datetime-local" value={code.until} onChange={(event) => setCode({ ...code, until: event.target.value })} /></label>
            <button className="identity-submit is-wide" disabled={busy === 'code'}>{busy === 'code' ? '创建中…' : '生成兑换码'}</button>
          </form>
        </section>

        <section className="admin-panel">
          <header><span>02 / BROADCAST</span><h2>全服邮件</h2></header>
          <form className="admin-form" onSubmit={(event) => void broadcast(event)}>
            <label><span>邮件类型</span><select value={mail.type} onChange={(event) => setMail({ ...mail, type: event.target.value as MailType })}><option value="ANNOUNCEMENT">公告</option><option value="NOTICE">通知</option><option value="REWARD">奖励</option></select></label>
            <label><span>标题</span><input required maxLength={80} value={mail.subject} onChange={(event) => setMail({ ...mail, subject: event.target.value })} /></label>
            <label className="is-wide"><span>正文</span><textarea required maxLength={2000} value={mail.body} onChange={(event) => setMail({ ...mail, body: event.target.value })} /></label>
            {mail.type === 'REWARD' && <>
              <label><span>筹码</span><input type="number" min={0} max={10000000} value={mail.rewardChips} onChange={(event) => setMail({ ...mail, rewardChips: Number(event.target.value) })} /></label>
              <label><span>英魂结晶</span><input type="number" min={0} max={1000000} value={mail.rewardCrystals} onChange={(event) => setMail({ ...mail, rewardCrystals: Number(event.target.value) })} /></label>
              <label className="is-wide"><span>皮肤 / 藏品</span><select value={mail.rewardSkinKey} onChange={(event) => setMail({ ...mail, rewardSkinKey: event.target.value })}><option value="">不附带</option>{rewardSkins.map((item) => <option key={item.key} value={item.key}>{item.name} · {item.key}</option>)}</select></label>
            </>}
            <button className="identity-submit is-wide" disabled={busy === 'mail'}>{busy === 'mail' ? '发送中…' : '发送给所有玩家'}</button>
          </form>
        </section>
      </div>

      <section className="admin-panel admin-code-list">
        <header><span>03 / CODE REGISTRY</span><h2>兑换码状态</h2></header>
        <div className="admin-table-wrap"><table><thead><tr><th>HASH 指纹</th><th>奖励</th><th>已用 / 上限</th><th>有效期</th><th>状态</th><th /></tr></thead><tbody>
          {codes.map((item) => <tr key={item.codeHash}><td><code>{item.codeHash.slice(0, 12)}…</code></td><td>{item.rewardAmount.toLocaleString()} {item.currency === 'CHIP' ? '筹码' : '英魂结晶'}</td><td>{item.redeemedCount.toLocaleString()} / {item.maxRedemptions?.toLocaleString() ?? '∞'}</td><td>{new Date(item.validFrom).toLocaleString()}<small>{item.validUntil ? `至 ${new Date(item.validUntil).toLocaleString()}` : '永久'}</small></td><td><i className={item.enabled ? 'is-on' : ''}>{item.enabled ? '启用' : '停用'}</i></td><td><button disabled={busy === item.codeHash} onClick={() => void toggleCode(item)}>{item.enabled ? '停用' : '启用'}</button></td></tr>)}
        </tbody></table></div>
      </section>

      <section className="admin-panel admin-account-registry">
        <header><span>04 / ACCOUNT REGISTRY</span><h2>账户与安全管理</h2></header>
        <div className="admin-account-layout">
          <div className="admin-account-list">{accounts.map((account) => <button key={account.accountId} className={selectedAccountId === account.accountId ? 'is-selected' : ''} onClick={() => setSelectedAccountId(account.accountId)}>
            <span className={account.online ? 'is-online' : ''}><i />{account.online ? 'ONLINE' : 'OFFLINE'}</span>
            <strong>{account.primaryGameId}</strong><small>真名：{account.realName}</small>
            <b>{account.wallet.chips.toLocaleString()} 筹码 · {account.wallet.spiritCrystals.toLocaleString()} 结晶</b>
          </button>)}</div>
          <div className="admin-account-editor">
            {accounts.filter((account) => account.accountId === selectedAccountId).map((account) => <div className="admin-account-summary" key={account.accountId}>
              <span>{account.administrator ? 'ADMINISTRATOR' : 'MASTER ACCOUNT'}</span><h3>{account.primaryGameId}</h3>
              <p>真名：{account.realName}</p><p>全部 ID：{account.gameIds.join(' / ')}</p>
              <p>密码：BCrypt 加密保护，原文不可查看</p>
            </div>)}
            <form className="admin-form" onSubmit={(event) => void adjustWallet(event)}>
              <label><span>筹码增减值</span><input type="number" min={-10000000} max={10000000} value={walletEdit.chips} onChange={(event) => setWalletEdit({ ...walletEdit, chips: event.target.value })} /></label>
              <label><span>英魂结晶增减值</span><input type="number" min={-1000000} max={1000000} value={walletEdit.crystals} onChange={(event) => setWalletEdit({ ...walletEdit, crystals: event.target.value })} /></label>
              <label className="is-wide"><span>调整原因（写入审计）</span><input required minLength={2} maxLength={200} value={walletEdit.reason} onChange={(event) => setWalletEdit({ ...walletEdit, reason: event.target.value })} /></label>
              <button className="identity-submit is-wide" disabled={busy === 'wallet' || !selectedAccountId || (Number(walletEdit.chips) === 0 && Number(walletEdit.crystals) === 0)}>确认货币调整</button>
            </form>
            <form className="admin-password-reset" onSubmit={(event) => void resetPassword(event)}>
              <label><span>重置为新密码</span><input required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={passwordReset} onChange={(event) => setPasswordReset(event.target.value)} /></label>
              <button disabled={busy === 'password' || !selectedAccountId}>重置并注销旧会话</button>
            </form>
          </div>
        </div>
      </section>

      <section className="admin-panel admin-friend-registry">
        <header><span>05 / RELATIONSHIP GRAPH</span><h2>好友关系管理</h2></header>
        <form className="admin-friend-create" onSubmit={(event) => void createFriendship(event)}>
          <select value={friendPair.first} onChange={(event) => setFriendPair({ ...friendPair, first: event.target.value })}>{accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.primaryGameId} · {account.realName}</option>)}</select>
          <span>↔</span>
          <select value={friendPair.second} onChange={(event) => setFriendPair({ ...friendPair, second: event.target.value })}>{accounts.map((account) => <option key={account.accountId} value={account.accountId}>{account.primaryGameId} · {account.realName}</option>)}</select>
          <button disabled={busy === 'friendship' || !friendPair.first || !friendPair.second || friendPair.first === friendPair.second}>建立好友关系</button>
        </form>
        <div className="admin-table-wrap"><table><thead><tr><th>发起账号</th><th>接收账号</th><th>关系状态</th><th>更新时间</th><th /></tr></thead><tbody>
          {friendships.map((item) => <tr key={item.friendshipId}><td>{item.requesterGameId}</td><td>{item.addresseeGameId}</td><td>{item.status === 'ACCEPTED' ? '好友' : '等待接受'}</td><td>{new Date(item.updatedAt).toLocaleString()}</td><td><button disabled={busy === item.friendshipId} onClick={() => void removeFriendship(item)}>解除</button></td></tr>)}
        </tbody></table></div>
      </section>
    </section>
  </main>
}
