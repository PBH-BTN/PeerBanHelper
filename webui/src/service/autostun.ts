import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type {
  AutoSTUNConfig,
  AutoSTUNStatus,
  BackgroundTaskInfo,
  ConnectionInfo,
  TunnelData
} from '@/api/model/autostun'
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

export async function getTunnelConnections(
  downloaderId: string
): Promise<CommonResponse<ConnectionInfo[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(
      endpointStore.endpoint,
      `api/autostun/tunnel/${encodeURIComponent(downloaderId)}/connections`
    ),
    location.href
  )
  
  console.log('Making API request to:', url.toString())
  
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    console.log('Response status:', res.status)
    console.log('Response ok:', res.ok)
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
