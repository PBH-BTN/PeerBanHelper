import { type BanList, type UnbanResult } from '@/api/model/banlist'
import { useEndpointStore } from '@/stores/endpoint'
import { type CommonResponse, type CommonResponseWithPage } from '@/api/model/common'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export interface BanListFilters {
  reason?: string
  clientName?: string
  peerId?: string
  country?: string
  city?: string
  asn?: string
  isp?: string
  netType?: string
  context?: string
  rule?: string
}

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
  filters?: BanListFilters
}): Promise<CommonResponseWithPage<BanList>> {
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

  // Add filter parameters
  if (params.filters) {
    if (params.filters.reason?.trim()) {
      url.searchParams.set('filterReason', encodeURIComponent(params.filters.reason.trim()))
    }
    if (params.filters.clientName?.trim()) {
      url.searchParams.set('filterClientName', encodeURIComponent(params.filters.clientName.trim()))
    }
    if (params.filters.peerId?.trim()) {
      url.searchParams.set('filterPeerId', encodeURIComponent(params.filters.peerId.trim()))
    }
    if (params.filters.country?.trim()) {
      url.searchParams.set('filterCountry', encodeURIComponent(params.filters.country.trim()))
    }
    if (params.filters.city?.trim()) {
      url.searchParams.set('filterCity', encodeURIComponent(params.filters.city.trim()))
    }
    if (params.filters.asn?.trim()) {
      url.searchParams.set('filterAsn', encodeURIComponent(params.filters.asn.trim()))
    }
    if (params.filters.isp?.trim()) {
      url.searchParams.set('filterIsp', encodeURIComponent(params.filters.isp.trim()))
    }
    if (params.filters.netType?.trim()) {
      url.searchParams.set('filterNetType', encodeURIComponent(params.filters.netType.trim()))
    }
    if (params.filters.context?.trim()) {
      url.searchParams.set('filterContext', encodeURIComponent(params.filters.context.trim()))
    }
    if (params.filters.rule?.trim()) {
      url.searchParams.set('filterRule', encodeURIComponent(params.filters.rule.trim()))
    }
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

export interface FilterOptions {
  clientNames: string[]
  countries: string[]
  cities: string[]
  asns: string[]
  isps: string[]
  netTypes: string[]
  torrents: Array<{ id: string; name: string }>
  rules: string[]
}

export async function getBanListFilterOptions(): Promise<CommonResponse<FilterOptions>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans/filter-options'), location.href)
  return fetch(url, {
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
