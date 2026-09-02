import { apiRequest } from './client'
import type { ArenaRoomSummary } from '../arena/types'

export const arenaApi = {
  listRooms: () => apiRequest<ArenaRoomSummary[]>('/api/arena/rooms'),
  createRoom: (roomName: string, maxPlayers: number) => apiRequest<ArenaRoomSummary>('/api/arena/rooms', {
    method: 'POST',
    body: JSON.stringify({ roomName, maxPlayers }),
  }),
}
