export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const fallback = `请求失败（HTTP ${response.status}）`
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    const error = new Error(body?.message ?? fallback) as Error & { status?: number }
    error.status = response.status
    throw error
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
