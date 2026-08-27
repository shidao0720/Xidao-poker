import { Component, type ReactNode } from 'react'

interface AppErrorBoundaryProps {
  children: ReactNode
}

interface AppErrorBoundaryState {
  failed: boolean
}

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(): void {
    // React 会在开发控制台保留原始错误；界面只展示安全的恢复入口。
  }

  render() {
    if (!this.state.failed) return this.props.children

    return (
      <main className="fatal-error" role="alert">
        <span className="fatal-error-mark" aria-hidden="true">!</span>
        <p className="eyebrow">CLIENT RECOVERY</p>
        <h1>牌桌界面加载失败</h1>
        <p>刷新后会从服务端重新获取完整牌局快照，不会使用当前页面里的残留状态。</p>
        <div>
          <button className="primary-button" onClick={() => window.location.reload()}>刷新并恢复</button>
          <button className="ghost-button" onClick={() => window.location.assign('/')}>返回大厅</button>
        </div>
      </main>
    )
  }
}
