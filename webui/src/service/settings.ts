import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { Config } from '@/api/model/config'
import type { Profile } from '@/api/model/profile'
import type { BTNStatus, RunningInfo } from '@/api/model/status'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function GetProfile(): Promise<CommonResponse<Profile>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/general/profile'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SaveProfile(config: Profile): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/general/profile'), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'PUT',
    body: JSON.stringify(config)
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetConfig(): Promise<CommonResponse<Config>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/general/config'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SaveConfig(config: Config): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/general/config'), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'PUT',
    body: JSON.stringify(config)
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function CheckModuleEnable(module: string): Promise<CommonResponse<boolean>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, 'api/general/checkModuleAvailable'),
    location.href
  )
  url.searchParams.set('module', module)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetRunningInfo(): Promise<CommonResponse<RunningInfo>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/general/status'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetBtnStatus(): Promise<CommonResponse<BTNStatus>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/modules/btn'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetHeapDumpFile() {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/general/heapdump'), location.href)
  if (import.meta.env.DEV) url.searchParams.set('token', endpointStore.authToken)
  return url
}
