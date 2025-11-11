import type { BanLog } from '@/api/model/banlogs'
import type { CommonResponseWithPage } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { appendSorterToUrl, getCommonHeader } from './utils'

export async function getBanlogs(params: {
  page: number
  pageSize?: number
  sorter?: string
}): Promise<CommonResponseWithPage<BanLog[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans/logs'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  if (params.sorter) {
    appendSorterToUrl(url, params.sorter)
  }

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
