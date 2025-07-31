import type { BanLog } from '@/api/model/banlogs'
import type { CommonResponseWithPage } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export interface BanLogFilters {
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
  torrentName?: string
  module?: string
}

export async function getBanlogs(params: {
  page: number
  pageSize?: number
  sorter?: string
  filters?: BanLogFilters
}): Promise<CommonResponseWithPage<BanLog[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/bans/logs'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  if (params.sorter) {
    url.searchParams.set('orderBy', params.sorter)
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
    if (params.filters.torrentName?.trim()) {
      url.searchParams.set('filterTorrentName', encodeURIComponent(params.filters.torrentName.trim()))
    }
    if (params.filters.module?.trim()) {
      url.searchParams.set('filterModule', encodeURIComponent(params.filters.module.trim()))
    }
  }

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
