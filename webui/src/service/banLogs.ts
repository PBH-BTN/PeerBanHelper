import type { BanLog } from '@/api/model/banlogs'
import type { CommonResponseWithPage } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { reactive } from 'vue'
import { getCommonHeader } from './utils'
import { snapshotCommentCounts } from './btnCommentCounts'

export async function getBanlogs(params: {
  page: number
  pageSize?: number
  sorter?: string
}): Promise<CommonResponseWithPage<BanLog>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const endpoint = endpointStore.endpoint
  const headers = getCommonHeader()
  const url = new URL(urlJoin(endpoint, '/api/bans/logs'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  if (params.sorter) {
    url.searchParams.set('orderBy', params.sorter)
  }

  return fetch(url, { headers }).then(async (res) => {
    endpointStore.assertResponseLogin(res)
    const result = reactive<CommonResponseWithPage<BanLog>>(await res.json())
    if (result.success && Array.isArray(result.data?.results)) {
      const rows = result.data.results
      const updateCounts = (counts: Map<string, number>) => {
        for (const row of rows) {
          row.btnCommentCount = counts.get(row.peerIp) ?? 0
        }
      }
      updateCounts(
        snapshotCommentCounts(
          endpoint,
          headers,
          rows.map((row) => row.peerIp),
          updateCounts
        )
      )
    }
    return result
  })
}
