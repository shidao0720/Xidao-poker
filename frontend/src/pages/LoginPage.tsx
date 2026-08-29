import { useState, type FormEvent } from 'react'
import { useAccountStore } from '../store/accountStore'

export function LoginPage() {
  const [kind, setKind] = useState<'login' | 'register'>('login')
  const [realName, setRealName] = useState('')
  const [gameId, setGameId] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const { authenticate, error, clearError } = useAccountStore()

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await authenticate(kind, { realName, gameId, password })
    } catch {
      // The store exposes a sanitized message beside the form.
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="identity-shell">
      <div className="identity-signal" aria-hidden="true" />
      <section className="identity-card">
        <img src="/assets/images/ui/fate-stay-poker-logo.png" alt="Fate stay poker" />
        <span className="identity-code">IDENTITY OBSERVATION GATE</span>
        <h1>{kind === 'login' ? 'Verify master identity' : 'Obtain master identity'}</h1>
        <p>真实姓名仅用于身份归属验证，不会展示给牌桌中的其他玩家。</p>

        <div className="identity-tabs" role="tablist">
          <button className={kind === 'login' ? 'is-active' : ''} onClick={() => { setKind('login'); clearError() }}>登录</button>
          <button className={kind === 'register' ? 'is-active' : ''} onClick={() => { setKind('register'); clearError() }}>注册 / 添加游戏 ID</button>
        </div>

        <form onSubmit={(event) => void submit(event)}>
          <label><span>真名</span><input name="realName" autoComplete="name" minLength={2} maxLength={64} required value={realName} onChange={(event) => setRealName(event.target.value)} /></label>
          <label><span>游戏 ID</span><input name="gameId" autoComplete="username" minLength={1} maxLength={12} required value={gameId} onChange={(event) => setGameId(event.target.value)} placeholder="1–12 个字符" /></label>
          <label><span>密码</span><input name="password" type="password" autoComplete={kind === 'login' ? 'current-password' : 'new-password'} minLength={8} maxLength={72} required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
          {error && <div className="identity-error" role="alert">{error}</div>}
          <button className="identity-submit" disabled={submitting}>{submitting ? '验证中…' : kind === 'login' ? '进入牌桌大厅' : '创建身份并领取 10,000 筹码'}</button>
        </form>
        <small className="play-money-notice">仅限娱乐积分 · 无现金价值 · 不支持充值、提现或玩家间转账</small>
      </section>
    </main>
  )
}
