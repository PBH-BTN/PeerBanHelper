import { type BanList, type UnbanResult } from '@/api/model/banlist'
import { useEndpointStore } from '@/stores/endpoint'
import { type CommonResponse } from '@/api/model/common'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

/**
 * Retrieves a list of banned IP addresses from the server.
 *
 * @param limit - Maximum number of ban entries to retrieve
 * @param search - Optional search term to filter ban list entries
 * @param lastBanTime - Optional timestamp to retrieve bans after a specific time
 * @returns A promise resolving to an array of ban list entries
 *
 * @remarks
 * This function fetches ban list data from the server with optional filtering capabilities.
 * It requires an active server connection and checks login status before returning results.
 *
 * @example
 * // Retrieve first 10 ban entries
 * const banList = await getBanList(10)
 *
 * @example
 * // Retrieve ban entries with a specific search term
 * const filteredBanList = await getBanList(20, '192.168.1')
 */
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
  if (search && search !== '') {
    url.searchParams.set('search', search)
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
