import type {rankItem} from '@/api/model/topban'
import {useEndpointStore} from '@/stores/endpoint'
import urlJoin from 'url-join'
import {appendSorterToUrl, getCommonHeader} from './utils'
import type {CommonResponseWithPage} from '@/api/model/common'

export async function getRanks(params: {
  page: number
  pageSize?: number
  filter?: string
  sorter?: string
}): Promise<CommonResponseWithPage<rankItem[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans/ranks'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  if (params.filter) {
    url.searchParams.set('filter', params.filter)
  }
  if (params.sorter) {
    appendSorterToUrl(url, params.sorter)
  }
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
