import type { CommonResponse } from '@/api/model/common'
import urlJoin from 'url-join'

const cache = new Map<string, { count: number; expires: number }>()
const cacheLifetime = 5 * 60_000
const maxCacheSize = 4000
let requestInFlight: Promise<Map<string, number>> | undefined
let retryAfter = 0

export function snapshotCommentCounts(
  endpoint: string,
  headers: Headers,
  addresses: string[],
  onUpdate?: (counts: Map<string, number>) => void
) {
  const now = Date.now()
  const counts = new Map<string, number>()
  const missing: string[] = []
  const cacheKey = (address: string) =>
    JSON.stringify([endpoint, headers.get('Authorization'), address])

  for (const address of new Set(addresses)) {
    const entry = cache.get(cacheKey(address))
    if (entry && entry.expires > now) {
      counts.set(address, entry.count)
    } else {
      counts.set(address, 0)
      missing.push(address)
    }
  }

  if (!missing.length || now < retryAfter) return counts

  const activeRequest = requestInFlight
  let request = activeRequest
  if (!request) {
    request = new Promise<void>((resolve) => setTimeout(resolve, 0))
      .then(() => fetchCommentCounts(endpoint, headers, missing, cacheKey))
      .finally(() => {
        requestInFlight = undefined
      })
    requestInFlight = request
  }

  if (onUpdate) {
    const mergeCounts = (updated: Map<string, number>) => {
      onUpdate(new Map([...counts, ...updated]))
    }
    void request.then((updated) => {
      if (activeRequest) {
        // Recheck only the misses after the pending request finishes.
        mergeCounts(snapshotCommentCounts(endpoint, headers, missing, mergeCounts))
      } else {
        mergeCounts(updated)
      }
    })
  }

  return counts
}

async function fetchCommentCounts(
  endpoint: string,
  headers: Headers,
  missing: string[],
  cacheKey: (address: string) => string
) {
  const counts = new Map<string, number>()
  try {
    const url = new URL(urlJoin(endpoint, '/api/peer/btnCommentCounts'), location.href)
    for (let offset = 0; offset < missing.length; offset += 50) {
      const batch = missing.slice(offset, offset + 50)
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(batch),
        signal: AbortSignal.timeout(30_000)
      })
      if (!response.ok) throw new Error(`Comment count request failed (HTTP ${response.status})`)
      const result: CommonResponse<Record<string, number>> = await response.json()
      if (!result.success || !result.data) throw new Error('Comment counts unavailable')

      const expires = Date.now() + cacheLifetime
      const entries = batch.map((address) => {
        const count = result.data[address]
        if (typeof count !== 'number' || !Number.isSafeInteger(count) || count < 0) {
          throw new Error('Invalid comment count response')
        }
        return [address, count] as const
      })
      for (const [address, count] of entries) {
        counts.set(address, count)
        const key = cacheKey(address)
        cache.delete(key)
        cache.set(key, { count, expires })
      }
      while (cache.size > maxCacheSize) {
        const oldest = cache.keys().next()
        if (oldest.done) break
        cache.delete(oldest.value)
      }
    }
  } catch (error) {
    retryAfter = Date.now() + 60_000
    console.warn('Unable to fetch BTN comment counts', error)
  }
  return counts
}
