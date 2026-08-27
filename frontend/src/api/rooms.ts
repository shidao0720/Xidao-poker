import type { CreateRoomInput, RoomSummary } from '../types/protocol'

const configuredBase = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${configuredBase}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const fallback = `请求失败（HTTP ${response.status}）`
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(body?.message ?? fallback)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const roomApi = {
  list: () => request<RoomSummary[]>('/api/rooms'),
  create: (input: CreateRoomInput) =>
    request<RoomSummary>('/api/rooms', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
}
