import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const outputPath = resolve(
  scriptDirectory,
  '../frontend/public/assets/audio/music/xidao-signal-loop.wav',
)

const sampleRate = 22_050
const durationSeconds = 16
const sampleCount = sampleRate * durationSeconds
const bytesPerSample = 2
const dataSize = sampleCount * bytesPerSample
const buffer = Buffer.alloc(44 + dataSize)

function writeWaveHeader() {
  buffer.write('RIFF', 0)
  buffer.writeUInt32LE(36 + dataSize, 4)
  buffer.write('WAVE', 8)
  buffer.write('fmt ', 12)
  buffer.writeUInt32LE(16, 16)
  buffer.writeUInt16LE(1, 20)
  buffer.writeUInt16LE(1, 22)
  buffer.writeUInt32LE(sampleRate, 24)
  buffer.writeUInt32LE(sampleRate * bytesPerSample, 28)
  buffer.writeUInt16LE(bytesPerSample, 32)
  buffer.writeUInt16LE(16, 34)
  buffer.write('data', 36)
  buffer.writeUInt32LE(dataSize, 40)
}

function oscillator(frequency, time, phase = 0) {
  return Math.sin(2 * Math.PI * frequency * time + phase)
}

function signalAt(time) {
  const loopPhase = time / durationSeconds
  const slowBreath = 0.62 + 0.38 * oscillator(2 / durationSeconds, time, -Math.PI / 2)
  const pulse = Math.max(0, oscillator(8 / durationSeconds, time)) ** 5
  const shimmer = Math.max(0, oscillator(16 / durationSeconds, time)) ** 9

  const lowSignal = oscillator(55, time) * 0.18
    + oscillator(82.5, time, 0.4) * 0.11
  const bluePad = oscillator(110, time + oscillator(1 / durationSeconds, time) * 0.0018) * 0.08
    + oscillator(165, time, 1.1) * 0.045
  const pulseTone = oscillator(220, time) * pulse * 0.055
    + oscillator(330, time, 0.6) * shimmer * 0.025
  const distantCarrier = oscillator(440, time, loopPhase * Math.PI * 2) * 0.012

  return Math.tanh((lowSignal * slowBreath + bluePad + pulseTone + distantCarrier) * 1.35) * 0.72
}

writeWaveHeader()
for (let index = 0; index < sampleCount; index += 1) {
  const time = index / sampleRate
  const value = Math.max(-1, Math.min(1, signalAt(time)))
  buffer.writeInt16LE(Math.round(value * 32_767), 44 + index * bytesPerSample)
}

mkdirSync(dirname(outputPath), { recursive: true })
writeFileSync(outputPath, buffer)
console.log(`Generated original public BGM: ${outputPath}`)
