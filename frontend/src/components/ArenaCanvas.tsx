import { useEffect, useRef, type PointerEvent as ReactPointerEvent } from 'react'
import type { ArenaPlayer, ArenaProjectile, ArenaSkill, ArenaSnapshot, VehicleControls } from '../arena/types'

interface ArenaCanvasProps {
  snapshot: ArenaSnapshot
  playerId: string
  onInput: (controls: VehicleControls) => void
}

const palette = ['#58ddff', '#ff587a', '#dfbd65', '#a76fff', '#63e5ac', '#ff9368', '#6a8cff', '#ff72c6', '#a8db61', '#e7eefc']
const idleControls: VehicleControls = { forward: false, backward: false, turnLeft: false, turnRight: false, fire: false }

interface VisualBody { x: number; y: number; angle: number }
interface CameraPosition { x: number; y: number; initialized: boolean }
interface CrystalShard { angle: number; speed: number; spin: number; size: number; stretch: number; delay: number }
interface DeathEffect { playerId: string; x: number; y: number; color: string; startedAt: number; shards: CrystalShard[] }

export function ArenaCanvas({ snapshot, playerId, onInput }: ArenaCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const targetRef = useRef(snapshot)
  const playerVisuals = useRef(new Map<string, VisualBody>())
  const projectileVisuals = useRef(new Map<number, { x: number; y: number }>())
  const deathEffects = useRef(new Map<string, DeathEffect>())
  const previousLifeStates = useRef(new Map<string, ArenaPlayer['lifeState']>())
  const cameraVisual = useRef<CameraPosition>({ x: 0, y: 0, initialized: false })
  const controlsRef = useRef<VehicleControls>({ ...idleControls })

  useEffect(() => {
    const now = performance.now()
    snapshot.players.forEach((player) => {
      const previous = previousLifeStates.current.get(player.id)
      if (previous === 'ALIVE' && player.lifeState === 'ELIMINATED') {
        deathEffects.current.set(player.id, createDeathEffect(player, now))
      } else if (player.lifeState === 'ALIVE') {
        deathEffects.current.delete(player.id)
      }
      previousLifeStates.current.set(player.id, player.lifeState)
    })
    const activeIds = new Set(snapshot.players.map((player) => player.id))
    for (const id of previousLifeStates.current.keys()) if (!activeIds.has(id)) previousLifeStates.current.delete(id)
    targetRef.current = snapshot
  }, [snapshot])

  useEffect(() => {
    let frame = 0
    const draw = () => {
      const canvas = canvasRef.current
      const state = targetRef.current
      if (canvas) renderArena(canvas, state, playerId, playerVisuals.current, projectileVisuals.current,
        deathEffects.current, cameraVisual.current, performance.now())
      frame = window.requestAnimationFrame(draw)
    }
    frame = window.requestAnimationFrame(draw)
    return () => window.cancelAnimationFrame(frame)
  }, [playerId])

  useEffect(() => {
    const update = (key: keyof VehicleControls, value: boolean) => {
      if (controlsRef.current[key] === value) return
      controlsRef.current = { ...controlsRef.current, [key]: value }
      onInput(controlsRef.current)
    }
    const keyFor = (event: KeyboardEvent): keyof VehicleControls | null => {
      if (event.code === 'KeyW' || event.code === 'ArrowUp') return 'forward'
      if (event.code === 'KeyS' || event.code === 'ArrowDown') return 'backward'
      if (event.code === 'KeyA' || event.code === 'ArrowLeft') return 'turnLeft'
      if (event.code === 'KeyD' || event.code === 'ArrowRight') return 'turnRight'
      if (event.code === 'Space') return 'fire'
      return null
    }
    const down = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return
      const key = keyFor(event)
      if (!key) return
      event.preventDefault()
      update(key, true)
    }
    const up = (event: KeyboardEvent) => {
      const key = keyFor(event)
      if (!key) return
      event.preventDefault()
      update(key, false)
    }
    const idle = () => {
      controlsRef.current = { ...idleControls }
      onInput(controlsRef.current)
    }
    window.addEventListener('keydown', down)
    window.addEventListener('keyup', up)
    window.addEventListener('blur', idle)
    return () => {
      window.removeEventListener('keydown', down)
      window.removeEventListener('keyup', up)
      window.removeEventListener('blur', idle)
      idle()
    }
  }, [onInput])

  function touch(key: keyof VehicleControls, value: boolean, event: ReactPointerEvent<HTMLButtonElement>) {
    event.preventDefault()
    if (value) event.currentTarget.setPointerCapture(event.pointerId)
    if (controlsRef.current[key] === value) return
    controlsRef.current = { ...controlsRef.current, [key]: value }
    onInput(controlsRef.current)
  }

  return (
    <div className="arena-canvas-shell">
      <canvas ref={canvasRef} width={1200} height={720} aria-label="载具竞技战场" />
      <div className="arena-touch-controls" aria-label="触控驾驶">
        <div className="arena-touch-move">
          <button aria-label="左转" onPointerDown={(event) => touch('turnLeft', true, event)} onPointerUp={(event) => touch('turnLeft', false, event)} onPointerCancel={(event) => touch('turnLeft', false, event)}>↶</button>
          <span>
            <button aria-label="前进" onPointerDown={(event) => touch('forward', true, event)} onPointerUp={(event) => touch('forward', false, event)} onPointerCancel={(event) => touch('forward', false, event)}>▲</button>
            <button aria-label="后退" onPointerDown={(event) => touch('backward', true, event)} onPointerUp={(event) => touch('backward', false, event)} onPointerCancel={(event) => touch('backward', false, event)}>▼</button>
          </span>
          <button aria-label="右转" onPointerDown={(event) => touch('turnRight', true, event)} onPointerUp={(event) => touch('turnRight', false, event)} onPointerCancel={(event) => touch('turnRight', false, event)}>↷</button>
        </div>
        <button className="arena-touch-fire" aria-label="开火" onPointerDown={(event) => touch('fire', true, event)} onPointerUp={(event) => touch('fire', false, event)} onPointerCancel={(event) => touch('fire', false, event)}>FIRE</button>
      </div>
    </div>
  )
}

function renderArena(
  canvas: HTMLCanvasElement,
  snapshot: ArenaSnapshot,
  selfId: string,
  playerVisuals: Map<string, VisualBody>,
  projectileVisuals: Map<number, { x: number; y: number }>,
  deathEffects: Map<string, DeathEffect>,
  camera: CameraPosition,
  now: number,
) {
  const context = canvas.getContext('2d')
  if (!context) return
  const width = snapshot.width || 1200
  const height = snapshot.height || 720
  context.clearRect(0, 0, canvas.width, canvas.height)

  const background = context.createRadialGradient(canvas.width * .5, canvas.height * .45, 20, canvas.width * .5, canvas.height * .45, canvas.width * .75)
  background.addColorStop(0, '#112c54')
  background.addColorStop(.48, '#091a37')
  background.addColorStop(1, '#030914')
  context.fillStyle = background
  context.fillRect(0, 0, canvas.width, canvas.height)

  const focus = snapshot.players.find((player) => player.id === selfId)
    ?? snapshot.players.find((player) => player.lifeState === 'ALIVE')
  const targetCameraX = clamp((focus?.x ?? width / 2) - canvas.width / 2, 0, Math.max(0, width - canvas.width))
  const targetCameraY = clamp((focus?.y ?? height / 2) - canvas.height / 2, 0, Math.max(0, height - canvas.height))
  if (!camera.initialized || Math.abs(targetCameraX - camera.x) > canvas.width * .65
    || Math.abs(targetCameraY - camera.y) > canvas.height * .65) {
    camera.x = targetCameraX
    camera.y = targetCameraY
    camera.initialized = true
  } else {
    camera.x += (targetCameraX - camera.x) * .16
    camera.y += (targetCameraY - camera.y) * .16
  }

  context.save()
  context.translate(-camera.x, -camera.y)
  drawGrid(context, width, height)

  context.save()
  context.strokeStyle = 'rgba(92,218,255,.32)'
  context.lineWidth = 2
  context.shadowColor = '#3acfff'
  context.shadowBlur = 13
  context.strokeRect(2, 2, width - 4, height - 4)
  context.restore()

  snapshot.walls.forEach((wall) => {
    const gradient = context.createLinearGradient(wall.x, wall.y, wall.x + wall.width, wall.y + wall.height)
    gradient.addColorStop(0, '#172b4d')
    gradient.addColorStop(.5, '#0a1429')
    gradient.addColorStop(1, '#22385d')
    context.fillStyle = gradient
    context.fillRect(wall.x, wall.y, wall.width, wall.height)
    context.strokeStyle = 'rgba(106,220,255,.45)'
    context.lineWidth = 1.5
    context.strokeRect(wall.x + .75, wall.y + .75, wall.width - 1.5, wall.height - 1.5)
  })

  snapshot.skills.forEach((skill) => drawSkill(context, skill))

  const activePlayerIds = new Set(snapshot.players.map((player) => player.id))
  for (const key of playerVisuals.keys()) if (!activePlayerIds.has(key)) playerVisuals.delete(key)
  snapshot.players.forEach((player) => {
    const visual = smoothPlayer(playerVisuals, player)
    if (player.lifeState !== 'ELIMINATED') drawVehicle(context, player, visual, selfId)
  })
  drawDeathEffects(context, deathEffects, now)

  const activeProjectileIds = new Set(snapshot.projectiles.map((projectile) => projectile.id))
  for (const key of projectileVisuals.keys()) if (!activeProjectileIds.has(key)) projectileVisuals.delete(key)
  snapshot.projectiles.forEach((projectile) => {
    const visual = smoothProjectile(projectileVisuals, projectile)
    const owner = snapshot.players.find((player) => player.id === projectile.ownerId)
    const color = palette[owner?.seat ?? 0] ?? '#8aeaff'
    context.save()
    context.fillStyle = '#f8fdff'
    context.shadowColor = color
    context.shadowBlur = 16
    context.beginPath()
    context.arc(visual.x, visual.y, 5.5, 0, Math.PI * 2)
    context.fill()
    context.restore()
  })

  context.restore()

  drawMiniMap(context, snapshot, selfId, camera, canvas.width, canvas.height)

  context.fillStyle = 'rgba(105,226,255,.35)'
  context.font = '11px Consolas, monospace'
  context.fillText(`ROUND ${String(snapshot.roundId).padStart(2, '0')} // TICK ${snapshot.tick}`, 18, 26)
}

function drawSkill(context: CanvasRenderingContext2D, skill: ArenaSkill) {
  const color = skill.type === 'OVERDRIVE' ? '#42e8ff' : skill.type === 'RAPID_FIRE' ? '#ff5c88' : '#d1b5ff'
  const symbol = skill.type === 'OVERDRIVE' ? '»' : skill.type === 'RAPID_FIRE' ? '✦' : '⬡'
  context.save()
  context.translate(skill.x, skill.y)
  context.shadowColor = color
  context.shadowBlur = 24
  context.fillStyle = 'rgba(4,12,27,.92)'
  context.strokeStyle = color
  context.lineWidth = 2
  context.beginPath()
  for (let side = 0; side < 6; side++) {
    const angle = Math.PI / 3 * side - Math.PI / 2
    const x = Math.cos(angle) * 18
    const y = Math.sin(angle) * 18
    if (side === 0) context.moveTo(x, y); else context.lineTo(x, y)
  }
  context.closePath(); context.fill(); context.stroke()
  context.fillStyle = color
  context.font = '700 17px Consolas, monospace'
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(symbol, 0, 1)
  context.strokeStyle = `${color}66`
  context.lineWidth = 1
  context.beginPath(); context.arc(0, 0, 25, 0, Math.PI * 2); context.stroke()
  context.restore()
}

function drawMiniMap(
  context: CanvasRenderingContext2D,
  snapshot: ArenaSnapshot,
  selfId: string,
  camera: CameraPosition,
  viewportWidth: number,
  viewportHeight: number,
) {
  const mapWidth = 174
  const mapHeight = mapWidth * snapshot.height / snapshot.width
  const left = viewportWidth - mapWidth - 16
  const top = 14
  const scale = mapWidth / snapshot.width
  context.save()
  context.fillStyle = 'rgba(2,8,19,.82)'
  context.fillRect(left, top, mapWidth, mapHeight)
  context.strokeStyle = 'rgba(91,219,255,.35)'
  context.strokeRect(left, top, mapWidth, mapHeight)
  context.fillStyle = 'rgba(127,187,220,.38)'
  snapshot.walls.forEach((wall) => context.fillRect(left + wall.x * scale, top + wall.y * scale,
    Math.max(1, wall.width * scale), Math.max(1, wall.height * scale)))
  snapshot.skills.forEach((skill) => {
    context.fillStyle = skill.type === 'OVERDRIVE' ? '#42e8ff' : skill.type === 'RAPID_FIRE' ? '#ff5c88' : '#d1b5ff'
    context.fillRect(left + skill.x * scale - 1.5, top + skill.y * scale - 1.5, 3, 3)
  })
  snapshot.players.filter((player) => player.lifeState === 'ALIVE').forEach((player) => {
    context.fillStyle = player.id === selfId ? '#fff' : palette[player.seat] ?? '#58ddff'
    context.beginPath(); context.arc(left + player.x * scale, top + player.y * scale,
      player.id === selfId ? 3 : 2, 0, Math.PI * 2); context.fill()
  })
  context.strokeStyle = 'rgba(255,255,255,.34)'
  context.strokeRect(left + camera.x * scale, top + camera.y * scale,
    Math.min(mapWidth, viewportWidth * scale), Math.min(mapHeight, viewportHeight * scale))
  context.restore()
}

function drawGrid(context: CanvasRenderingContext2D, width: number, height: number) {
  context.save()
  context.strokeStyle = 'rgba(87,182,231,.055)'
  context.lineWidth = 1
  for (let x = 0; x <= width; x += 40) {
    context.beginPath(); context.moveTo(x, 0); context.lineTo(x, height); context.stroke()
  }
  for (let y = 0; y <= height; y += 40) {
    context.beginPath(); context.moveTo(0, y); context.lineTo(width, y); context.stroke()
  }
  context.strokeStyle = 'rgba(116,214,255,.08)'
  context.beginPath(); context.arc(width / 2, height / 2, 92, 0, Math.PI * 2); context.stroke()
  context.restore()
}

function drawVehicle(context: CanvasRenderingContext2D, player: ArenaPlayer, visual: VisualBody, selfId: string) {
  const color = palette[player.seat] ?? '#58ddff'
  context.save()
  context.translate(visual.x, visual.y)
  context.rotate(visual.angle)
  context.globalAlpha = player.lifeState === 'ALIVE' ? 1 : .48
  context.shadowColor = color
  context.shadowBlur = player.id === selfId ? 18 : 9
  context.fillStyle = '#050b16'
  context.fillRect(-20, -17, 38, 7)
  context.fillRect(-20, 10, 38, 7)
  context.fillStyle = color
  if (player.speedBoosted) {
    context.strokeStyle = '#52edff'; context.lineWidth = 3
    context.beginPath(); context.moveTo(-24, -8); context.lineTo(-38, -8); context.moveTo(-24, 8); context.lineTo(-42, 8); context.stroke()
  }
  context.beginPath()
  context.moveTo(22, 0); context.lineTo(11, -14); context.lineTo(-16, -12); context.lineTo(-20, 0); context.lineTo(-16, 12); context.lineTo(11, 14); context.closePath(); context.fill()
  context.fillStyle = '#0b1a31'
  context.beginPath(); context.arc(1, 0, 9, 0, Math.PI * 2); context.fill()
  context.strokeStyle = '#e6fbff'
  context.lineWidth = 4
  context.beginPath(); context.moveTo(2, 0); context.lineTo(27, 0); context.stroke()
  if (player.id === selfId) {
    context.strokeStyle = '#fff'
    context.lineWidth = 1.5
    context.beginPath(); context.arc(0, 0, 25, 0, Math.PI * 2); context.stroke()
  }
  if (player.shielded) {
    context.strokeStyle = '#d6beff'
    context.lineWidth = 3
    context.shadowColor = '#9e70ff'
    context.shadowBlur = 18
    context.beginPath(); context.arc(0, 0, 29, 0, Math.PI * 2); context.stroke()
  }
  if (player.rapidFire) {
    context.fillStyle = '#ff6d95'
    context.beginPath(); context.arc(25, -15, 4, 0, Math.PI * 2); context.fill()
  }
  context.restore()
  context.save()
  context.textAlign = 'center'
  context.font = player.id === selfId ? '700 12px Consolas, monospace' : '11px Consolas, monospace'
  context.fillStyle = player.connected ? '#eefaff' : '#75859f'
  context.shadowColor = '#02040a'
  context.shadowBlur = 5
  context.fillText(player.name, visual.x, visual.y - 29)
  context.restore()
}

function createDeathEffect(player: ArenaPlayer, startedAt: number): DeathEffect {
  const seed = hashText(`${player.id}:${player.seat}`)
  const shards = Array.from({ length: 24 }, (_, index): CrystalShard => ({
    angle: seededNoise(seed + index * 17) * Math.PI * 2,
    speed: 42 + seededNoise(seed + index * 29 + 3) * 128,
    spin: (seededNoise(seed + index * 41 + 7) - .5) * 9,
    size: 3 + seededNoise(seed + index * 53 + 11) * 8,
    stretch: 1.2 + seededNoise(seed + index * 67 + 13) * 2.7,
    delay: seededNoise(seed + index * 79 + 19) * 170,
  }))
  return { playerId: player.id, x: player.x, y: player.y, color: palette[player.seat] ?? '#58ddff', startedAt, shards }
}

function drawDeathEffects(context: CanvasRenderingContext2D, effects: Map<string, DeathEffect>, now: number) {
  for (const [playerId, effect] of effects) {
    const elapsed = now - effect.startedAt
    if (elapsed >= 1_600) {
      effects.delete(playerId)
      continue
    }
    const burst = clamp(elapsed / 280, 0, 1)
    const fade = 1 - clamp((elapsed - 760) / 840, 0, 1)
    context.save()
    context.translate(effect.x, effect.y)
    context.globalAlpha = fade
    context.strokeStyle = effect.color
    context.lineWidth = 2.4 * (1 - burst) + .5
    context.shadowColor = effect.color
    context.shadowBlur = 28 * fade
    context.beginPath()
    context.arc(0, 0, 8 + burst * 39, 0, Math.PI * 2)
    context.stroke()
    if (elapsed < 230) {
      const flash = 1 - elapsed / 230
      context.globalAlpha = flash * .75
      context.fillStyle = '#edfdff'
      context.beginPath(); context.arc(0, 0, 23 * flash + 4, 0, Math.PI * 2); context.fill()
    }
    context.restore()

    effect.shards.forEach((shard, index) => {
      const shardElapsed = elapsed - shard.delay
      if (shardElapsed <= 0) return
      const progress = clamp(shardElapsed / (1_420 - shard.delay), 0, 1)
      const travel = 1 - Math.pow(1 - progress, 2.2)
      const distance = shard.speed * travel
      const x = effect.x + Math.cos(shard.angle) * distance
      const y = effect.y + Math.sin(shard.angle) * distance + progress * progress * 24
      const alpha = Math.pow(1 - progress, 1.25)
      context.save()
      context.translate(x, y)
      context.rotate(shard.angle + shard.spin * progress)
      context.globalAlpha = alpha
      context.fillStyle = index % 4 === 0 ? '#e8fdff' : effect.color
      context.strokeStyle = index % 3 === 0 ? '#ffffff' : effect.color
      context.lineWidth = .7
      context.shadowColor = effect.color
      context.shadowBlur = 13 * alpha
      context.beginPath()
      context.moveTo(shard.size * shard.stretch, 0)
      context.lineTo(-shard.size * .7, shard.size * .55)
      context.lineTo(-shard.size * .32, -shard.size * .7)
      context.closePath(); context.fill(); context.stroke()
      context.restore()
    })
  }
}

function hashText(value: string) {
  let hash = 2166136261
  for (let index = 0; index < value.length; index++) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

function seededNoise(seed: number) {
  const value = Math.sin(seed * 12.9898 + 78.233) * 43758.5453
  return value - Math.floor(value)
}

function smoothPlayer(visuals: Map<string, VisualBody>, player: ArenaPlayer): VisualBody {
  const current = visuals.get(player.id) ?? { x: player.x, y: player.y, angle: player.angle }
  current.x += (player.x - current.x) * .38
  current.y += (player.y - current.y) * .38
  let delta = player.angle - current.angle
  if (delta > Math.PI) delta -= Math.PI * 2
  if (delta < -Math.PI) delta += Math.PI * 2
  current.angle += delta * .42
  visuals.set(player.id, current)
  return current
}

function smoothProjectile(visuals: Map<number, { x: number; y: number }>, projectile: ArenaProjectile) {
  const current = visuals.get(projectile.id) ?? { x: projectile.x, y: projectile.y }
  current.x += (projectile.x - current.x) * .62
  current.y += (projectile.y - current.y) * .62
  visuals.set(projectile.id, current)
  return current
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(maximum, value))
}
