import { type BanList, type UnbanResult } from '@/api/model/banlist'
import { useEndpointStore } from '@/stores/endpoint'
import { type CommonResponse } from '@/api/model/common'
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
