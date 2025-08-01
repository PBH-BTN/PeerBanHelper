import { type BanList, type UnbanResult } from '@/api/model/banlist'
import { useEndpointStore } from '@/stores/endpoint'
import { type CommonResponse, type CommonResponseWithPage } from '@/api/model/common'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function getBanList(
  limit: number,
  search?: string,
  lastBanTime?: number
): Promise<CommonResponse<BanList[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans'), location.href)
  url.searchParams.set('limit', String(limit))
  if (lastBanTime) {
    url.searchParams.set('lastBanTime', String(lastBanTime))
  }
  if (search && search.trim() !== '') {
    url.searchParams.set('search', encodeURIComponent(search.trim()))
  }
  return fetch(url, {
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

/**
 * New paginated ban list API.
 * It uses `page`/`pageSize` query parameters and returns a paginated response.
 * The backend will return results sorted by ban time (desc) by default.
 */
export async function getBanListPaginated(params: {
  page: number
  pageSize?: number
  search?: string
}): Promise<CommonResponseWithPage<BanList[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  if (params.search && params.search.trim() !== '') {
    url.searchParams.set('search', encodeURIComponent(params.search.trim()))
  }

  return fetch(url, {
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function unbanIP(ip: string): Promise<CommonResponse<UnbanResult>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans'), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'DELETE',
    body: JSON.stringify([ip])
  }).then((res) => {
    return res.json()
  })
}
