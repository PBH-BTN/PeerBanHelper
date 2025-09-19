import type {
  AutoSTUNConfig,
  AutoSTUNStatus,
  BackgroundTaskInfo,
  ConnectionInfo,
  TunnelData
} from '@/api/model/autostun'
import type {
  CommonResponse,
  CommonResponseWithoutData,
  CommonResponseWithPage
} from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function getAutoSTUNStatus(): Promise<CommonResponse<AutoSTUNStatus>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/autostun/status'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function saveAutoSTUNConfig(
  config: AutoSTUNConfig
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/autostun/config'), location.href)
  return fetch(url, {
    method: 'PUT',
    headers: getCommonHeader(),
    body: JSON.stringify(config)
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getAutoSTUNTunnels(): Promise<CommonResponse<TunnelData[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/autostun/tunnels'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getTunnelConnections(params: {
  downloaderId: string
  page: number
  pageSize?: number
}): Promise<CommonResponseWithPage<ConnectionInfo[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(
      endpointStore.endpoint,
      `api/autostun/tunnel/${encodeURIComponent(params.downloaderId)}/connections`
    ),
    location.href
  )
  if (params.page) {
    url.searchParams.set('page', String(params.page))
  }
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function refreshNATType(): Promise<
  CommonResponse<null> & { backgroundTask: BackgroundTaskInfo }
> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/autostun/refreshNatType'), location.href)
  return fetch(url, {
    method: 'POST',
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function restartAutoSTUN(): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/autostun/restart'), location.href)
  return fetch(url, {
    method: 'POST',
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
