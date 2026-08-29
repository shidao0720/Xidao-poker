import type { CreateRoomInput, RoomSummary } from '../types/protocol'
import { apiRequest } from './client'

export const roomApi = {
  list: () => apiRequest<RoomSummary[]>('/api/rooms'),
  create: (input: CreateRoomInput) =>
    apiRequest<RoomSummary>('/api/rooms', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
}
