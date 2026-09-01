const PENDING_KEY = 'xidao-poker.pending-table-result'
const RESULT_KEY = 'xidao-poker.table-result'

export interface PendingTableResult {
  roomId: string
  startedAt: number
}

export interface TableResultNotice {
  netChips: number
  buyIn: number
  returnedChips: number
}

function read<T>(key: string): T | null {
  const raw = sessionStorage.getItem(key)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    sessionStorage.removeItem(key)
    return null
  }
}

export function rememberPendingTableResult(roomId: string): void {
  sessionStorage.setItem(PENDING_KEY, JSON.stringify({ roomId, startedAt: Date.now() }))
}

export function pendingTableResult(): PendingTableResult | null {
  return read<PendingTableResult>(PENDING_KEY)
}

export function clearPendingTableResult(): void {
  sessionStorage.removeItem(PENDING_KEY)
}

export function saveTableResult(result: TableResultNotice): void {
  sessionStorage.setItem(RESULT_KEY, JSON.stringify(result))
}

export function takeTableResult(): TableResultNotice | null {
  const result = read<TableResultNotice>(RESULT_KEY)
  sessionStorage.removeItem(RESULT_KEY)
  return result
}
