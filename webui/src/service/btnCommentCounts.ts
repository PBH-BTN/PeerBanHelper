import type { CommonResponse } from '@/api/model/common'
import urlJoin from 'url-join'

const cache = new Map<string, { count: number; expires: number }>()
const cacheLifetime = 5 * 60_000
const maxCacheSize = 4000
let requestInFlight = false
let retryAfter = 0

export function snapshotCommentCounts(endpoint: string, headers: Headers, addresses: string[]) {
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
      missing.push(address)
    }
  }

  if (missing.length && !requestInFlight && now >= retryAfter) {
    requestInFlight = true
    setTimeout(async () => {
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
          if (!response.ok) throw new Error('Comment count request failed')
          const result: CommonResponse<Record<string, number>> = await response.json()
          if (!result.success || !result.data) throw new Error('Comment counts unavailable')

          for (const address of batch) {
            const count = result.data[address]
            if (typeof count === 'number' && Number.isSafeInteger(count) && count >= 0) {
              const key = cacheKey(address)
              cache.delete(key)
              cache.set(key, { count, expires: Date.now() + cacheLifetime })
            }
          }
          while (cache.size > maxCacheSize) {
            const oldest = cache.keys().next()
            if (oldest.done) break
            cache.delete(oldest.value)
          }
        }
      } catch {
        retryAfter = Date.now() + 60_000
      } finally {
        requestInFlight = false
      }
    }, 0)
  }

  return counts
}
