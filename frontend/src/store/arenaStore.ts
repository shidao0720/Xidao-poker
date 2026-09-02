import { create } from 'zustand'
import type { ArenaConnectionState, ArenaSnapshot, VehicleControls } from '../arena/types'
import { ArenaSocket, type ArenaIdentity } from '../ws/ArenaSocket'

interface ArenaState {
  roomId: string | null
  playerId: string | null
  snapshot: ArenaSnapshot | null
  sequence: number
  connection: ArenaConnectionState
  error: string | null
  connect: (roomId: string, identity: ArenaIdentity) => void
  disconnect: () => void
  leave: () => void
  ready: (ready: boolean) => void
  start: () => void
  input: (controls: VehicleControls) => void
  clearError: () => void
}

let activeSocket: ArenaSocket | null = null

export const useArenaStore = create<ArenaState>((set, get) => ({
  roomId: null,
  playerId: null,
  snapshot: null,
  sequence: 0,
  connection: 'idle',
  error: null,

  connect: (roomId, identity) => {
    activeSocket?.close()
    set({ roomId, playerId: identity.playerId, snapshot: null, sequence: 0, connection: 'connecting', error: null })
    activeSocket = new ArenaSocket(roomId, identity, {
      onSnapshot: (snapshot, sequence) => {
        if (get().roomId !== roomId || sequence < get().sequence) return
        set({ snapshot, sequence, error: null })
      },
      onConnection: (connection) => set({ connection }),
      onError: (error) => set({ error }),
    })
    activeSocket.connect()
  },

  disconnect: () => {
    activeSocket?.close()
    activeSocket = null
    set({ roomId: null, playerId: null, snapshot: null, sequence: 0, connection: 'idle', error: null })
  },

  leave: () => {
    const socket = activeSocket
    socket?.leave()
    window.setTimeout(() => socket?.close(), 80)
    activeSocket = null
    set({ roomId: null, playerId: null, snapshot: null, sequence: 0, connection: 'idle', error: null })
  },

  ready: (ready) => activeSocket?.ready(ready),
  start: () => activeSocket?.start(),
  input: (controls) => activeSocket?.input(controls),
  clearError: () => set({ error: null }),
}))
