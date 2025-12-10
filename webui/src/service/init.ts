import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { CreateDownloadRequest, ScanDownloaderInfo } from '@/api/model/downloader'
import type { InitReq } from '@/api/model/init'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function InitPBH(req: InitReq): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()

  const url = new URL(urlJoin(endpointStore.endpoint, '/api/oobe/init'), location.href)

  return fetch(url, { headers: getCommonHeader(), body: JSON.stringify(req), method: 'POST' }).then(
    (res) => res.json()
  )
}

export async function TestDownloaderConfig(
  req: CreateDownloadRequest
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  const url = new URL(urlJoin(endpointStore.endpoint, `/api/oobe/testDownloader`), location.href)
  return fetch(url, { method: 'POST', headers: getCommonHeader(), body: JSON.stringify(req) }).then(
    (res) => {
      endpointStore.assertResponseLogin(res)
      return res.json()
    }
  )
}

export async function SacnDownloader(): Promise<CommonResponse<ScanDownloaderInfo[]>> {
  const endpointStore = useEndpointStore()
  const url = new URL(urlJoin(endpointStore.endpoint, `/api/oobe/scanDownloader`), location.href)
  return fetch(url, { method: 'POST', headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
