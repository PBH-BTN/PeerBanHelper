import type { CommonResponse } from '@/api/model/common'
import type { AnalysisField, BanTrends, GeoIP, Traffic, Trends } from '@/api/model/statistic'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function getAnalysisDataByField(
  field: 'peerId' | 'torrentName' | 'module',
  filter = false,
  downloader?: string
): Promise<CommonResponse<AnalysisField[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const query = new URLSearchParams({
    type: 'count',
    field,
    filter: filter ? '0.01' : '0'
  })
  if (downloader) {
    query.append('downloader', downloader)
  }
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/statistic/analysis/field?` + query.toString()),
    location.href
  )

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getGeoIPData(
  startAt: Date,
  endAt: Date,
  bannedOnly: boolean,
  downloader?: string
): Promise<CommonResponse<GeoIP>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, `api/chart/geoIpInfo`), location.href)

  // Convert dates to Unix timestamp in milliseconds
  const startAtTimestamp = startAt.getTime()
  const endAtTimestamp = endAt.getTime()

  // Add query parameters for startAt and endAt
  url.searchParams.append('startAt', startAtTimestamp.toString())
  url.searchParams.append('endAt', endAtTimestamp.toString())
  url.searchParams.append('bannedOnly', String(bannedOnly))
  if (downloader) {
    url.searchParams.append('downloader', downloader)
  }
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getBanTrends(
  startAt: Date,
  endAt: Date,
  downloader?: string
): Promise<CommonResponse<BanTrends[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const query = new URLSearchParams({
    startAt: startAt.getTime().toString(),
    endAt: endAt.getTime().toString()
  })
  if (downloader) {
    query.append('downloader', downloader)
  }
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/statistic/analysis/banTrends?` + query.toString()),
    location.href
  )

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getTrends(
  startAt: Date,
  endAt: Date,
  downloader?: string
): Promise<CommonResponse<Trends>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const query = new URLSearchParams({
    startAt: startAt.getTime().toString(),
    endAt: endAt.getTime().toString()
  })
  if (downloader) {
    query.append('downloader', downloader)
  }
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/chart/trend?` + query.toString()),
    location.href
  )

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getTraffic(
  startAt: Date,
  endAt: Date,
  downloader?: string
): Promise<CommonResponse<Traffic[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const query = new URLSearchParams({
    startAt: startAt.getTime().toString(),
    endAt: endAt.getTime().toString()
  })
  if (downloader) {
    query.append('downloader', downloader)
  }
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/chart/traffic?` + query.toString()),
    location.href
  )

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
