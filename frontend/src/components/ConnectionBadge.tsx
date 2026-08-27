import type { ConnectionState } from '../types/protocol'

const labels: Record<ConnectionState, string> = {
  idle: '未连接',
  connecting: '连接中',
  connected: '实时连接',
  reconnecting: '正在重连',
  closed: '已离开',
}

export function ConnectionBadge({ state }: { state: ConnectionState }) {
  return (
    <span className={`connection-badge connection-${state}`}>
      <i aria-hidden="true" />
      {labels[state]}
    </span>
  )
}
