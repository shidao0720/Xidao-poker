import { useEffect, useRef } from 'react'

const MUSIC_PATH = '/assets/audio/music/gate-of-steiner.mp4'

export function BackgroundMusic({ paused }: { paused: boolean }) {
  const audioRef = useRef<HTMLAudioElement>(null)

  useEffect(() => {
    const audio = audioRef.current
    if (!audio) return
    audio.volume = 0.22

    if (paused) {
      audio.pause()
      return
    }
    const play = () => { void audio.play().catch(() => undefined) }
    play()
    window.addEventListener('pointerdown', play, { once: true })
    return () => {
      window.removeEventListener('pointerdown', play)
      window.removeEventListener('pointerdown', play)
    }
  }, [paused])

  return <audio ref={audioRef} src={MUSIC_PATH} loop preload="auto" />
}
