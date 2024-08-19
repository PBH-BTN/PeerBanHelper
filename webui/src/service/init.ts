import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
import type { InitReq } from '@/api/model/init'
import type { CommonResponseWithoutData } from '@/api/model/common'
import type { CreateDownloadRequest } from '@/api/model/downloader'

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
