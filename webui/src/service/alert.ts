import type { Alert } from '@/api/model/alert'
import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function GetUnreadAlerts(): Promise<CommonResponse<Alert[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/alerts?unread=true'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function DismissAlert(id: number): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/alert/${id}/dismiss`), location.href)
  return fetch(url, { headers: getCommonHeader(), method: 'PATCH' }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function DismissAll(): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/alert/dismissAll'), location.href)
  return fetch(url, { headers: getCommonHeader(), method: 'POST' }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
