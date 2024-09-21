import { useEndpointStore } from '@/stores/endpoint'
import { type CommonResponse } from '@/api/model/common'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
import type { ConfigSaveResult } from '@/api/model/config'

export async function getConfigYamlList(): Promise<CommonResponse<string[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/config'), location.href)
  return fetch(url, {
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function getConfigYamlContent(configId: string): Promise<CommonResponse<string>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/config/${configId}`), location.href)
  return fetch(url, {
    headers: getCommonHeader()
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function setConfigYamlContent(
  configId: string,
  content: string
): Promise<CommonResponse<ConfigSaveResult[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/config/${configId}`), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'PUT',
    body: JSON.stringify({ content })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
