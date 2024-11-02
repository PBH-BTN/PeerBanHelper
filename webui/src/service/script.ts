import type {
  CommonResponse,
  CommonResponseWithoutData,
  CommonResponseWithPage
} from '@/api/model/common'
import type { Script } from '@/api/model/script'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export class InsecureNetworkError extends Error {
  static name = 'InsecureNetworkError' as const
  name = InsecureNetworkError.name

  static is(err: unknown): err is InsecureNetworkError {
    return (err as Error)?.name === InsecureNetworkError.name
  }
}

export async function GetScriptList(params: {
  page: number
  pageSize?: number
}): Promise<CommonResponseWithPage<Script>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, 'api/expression-engine/scripts'),
    location.href
  )
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    return res.json()
  })
}

export async function GetScriptContent(id: string): Promise<CommonResponse<string>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/expression-engine/${id}`), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    return res.json()
  })
}

export async function DeleteScript(id: string): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/expression-engine/${id}`), location.href)
  return fetch(url, { headers: getCommonHeader(), method: 'DELETE' }).then((res) => {
    return res.json()
  })
}

export async function UpsertScript(
  name: string,
  content: string
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/expression-engine/${name}`),
    location.href
  )
  return fetch(url, { headers: getCommonHeader(), method: 'PUT', body: content }).then((res) => {
    return res.json()
  })
}
