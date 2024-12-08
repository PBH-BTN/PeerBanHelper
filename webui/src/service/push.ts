import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { PushConfig } from '@/api/model/push'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function GetPushChannelList(): Promise<CommonResponse<PushConfig[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, `api/push`), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function CreatePushChannel(channel: PushConfig): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, `api/push`), location.href)
  return fetch(url, {
    method: 'PUT',
    headers: getCommonHeader(),
    body: JSON.stringify(channel)
  }).then(async (res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function UpdatePushChannel(
  target: string,
  channel: PushConfig
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/push/${encodeURIComponent(target)}`),
    location.href
  )
  return fetch(url, {
    method: 'PATCH',
    headers: getCommonHeader(),
    body: JSON.stringify(channel)
  }).then(async (res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function TestPushChannel(channel: PushConfig): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, `api/push/test`), location.href)
  return fetch(url, {
    method: 'POST',
    headers: getCommonHeader(),
    body: JSON.stringify(channel)
  }).then(async (res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function DeletePushChannel(name: string): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/push/${encodeURIComponent(name)}`),
    location.href
  )
  return fetch(url, {
    method: 'DELETE',
    headers: getCommonHeader()
  }).then(async (res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
