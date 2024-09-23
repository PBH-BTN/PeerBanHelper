import type { CommonResponse, CommonResponseWithPage } from '@/api/model/common'
import type { AccessHistory, IPBasicInfo, TorrentInfo } from '@/api/model/data'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function GetTorrentInfoList(params: {
  page: number
  pageSize?: number
  keyword?: string
}): Promise<CommonResponseWithPage<TorrentInfo[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/torrent/query'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  if (params.keyword) {
    url.searchParams.set('keyword', params.keyword)
  }

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetAccessHistoryList(params: {
  infoHash: string
  page: number
  pageSize?: number
}): Promise<CommonResponseWithPage<AccessHistory[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/torrent/${params.infoHash}/accessHistory`),
    location.href
  )
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetIPBasicData(ip: string): Promise<CommonResponse<IPBasicInfo>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/peer/${ip}`), location.href)

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
