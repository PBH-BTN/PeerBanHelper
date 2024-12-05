import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type {
  ClientStatus,
  CreateDownloadRequest,
  Downloader,
  PeerInfo,
  Torrent
} from '@/api/model/downloader'
import type { Statistic } from '@/api/model/statistic'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function getClientStatus(name: string): Promise<CommonResponse<ClientStatus>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/downloaders/${encodeURIComponent(name)}/status`),
    location.href
  )
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getStatistic(): Promise<CommonResponse<Statistic>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, '/api/statistic/counter'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getDownloaders(): Promise<CommonResponse<Downloader[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, '/api/downloaders'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getTorrents(downloader: string): Promise<CommonResponse<Torrent[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(endpointStore.endpoint, `/api/downloaders/${encodeURIComponent(downloader)}/torrents`),
    location.href
  )
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function CreateDownloader(
  req: CreateDownloadRequest
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, `/api/downloaders`), location.href)
  return fetch(url, { method: 'PUT', headers: getCommonHeader(), body: JSON.stringify(req) }).then(
    async (res) => {
      endpointStore.assertResponseLogin(res)
      return res.json()
    }
  )
}

export async function UpdateDownloader(
  target: string,
  req: CreateDownloadRequest
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(endpointStore.endpoint, `/api/downloaders/${encodeURIComponent(target)}`),
    location.href
  )
  return fetch(url, {
    method: 'PATCH',
    headers: getCommonHeader(),
    body: JSON.stringify(req)
  }).then(async (res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function TestDownloaderConfig(
  req: CreateDownloadRequest
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, `/api/downloaders/test`), location.href)
  return fetch(url, { method: 'POST', headers: getCommonHeader(), body: JSON.stringify(req) }).then(
    (res) => {
      endpointStore.assertResponseLogin(res)
      return res.json()
    }
  )
}

export async function DeleteDownloader(name: string): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(endpointStore.endpoint, `/api/downloaders/${encodeURIComponent(name)}`),
    location.href
  )
  return fetch(url, { method: 'DELETE', headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getPeer(
  downloader: string,
  torrentId: string
): Promise<CommonResponse<PeerInfo[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(
      endpointStore.endpoint,
      `/api/downloaders/${encodeURIComponent(downloader)}/torrent/${torrentId}/peers`
    ),
    location.href
  )
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
