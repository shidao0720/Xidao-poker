type RandomBytes = Uint8Array<ArrayBuffer>
type RandomByteFiller = (bytes: RandomBytes) => void

function fillRandomBytes(bytes: RandomBytes): void {
  const cryptoApi = globalThis.crypto
  if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
    cryptoApi.getRandomValues(bytes)
    return
  }

  // 玩家 ID 和 commandId 只用于客户端去重与身份键，不承担重连令牌的安全职责。
  // 真正的 resumeToken 始终由服务端使用 SecureRandom 生成。
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Math.floor(Math.random() * 256)
  }
}

export function createRandomId(prefix = '', fill: RandomByteFiller = fillRandomBytes): string {
  const bytes = new Uint8Array(new ArrayBuffer(16))
  fill(bytes)
  const body = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
  return `${prefix}${body}`
}
