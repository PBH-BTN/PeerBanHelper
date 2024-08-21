import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
import type {
  CommonResponse,
  CommonResponseWithoutData,
  CommonResponseWithPage
} from '@/api/model/common'
import type { ruleBrief, updateLog } from '@/api/model/ruleSubscribe'

export async function getRuleList(): Promise<CommonResponse<ruleBrief[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/sub/rules'), location.href)

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function ToggleRuleEnable(
  ruleId: string,
  enable: boolean
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/sub/rule/${ruleId}`), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'PATCH',
    body: JSON.stringify({ enabled: enable })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function UpdateRuleItem({
  ruleId,
  ruleName,
  subUrl
}: {
  ruleId: string
  ruleName: string
  subUrl: string
}): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `/api/sub/rule/${ruleId}`), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'POST',
    body: JSON.stringify({ ruleName, subUrl })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function AddRuleItem({
  ruleId,
  ruleName,
  subUrl
}: {
  ruleId: string
  ruleName: string
  subUrl: string
}): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/sub/rule`), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'PUT',
    body: JSON.stringify({ ruleId, ruleName, subUrl })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function RefreshRule(ruleId: string): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/sub/rule/${ruleId}/update`),
    location.href
  )
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'POST'
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function DeleteRule(ruleId: string): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `/api/sub/rule/${ruleId}`), location.href)
  return fetch(url, {
    headers: getCommonHeader(),
    method: 'DELETE'
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function UpdateAll() {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `/api/sub/rules/update`), location.href)
  const header = getCommonHeader()
  header.set('X-Audit', 'Rule deletion')
  return fetch(url, {
    headers: header,
    method: 'POST'
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetCheckInvervalSettings(): Promise<CommonResponse<number>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/sub/interval'), location.href)

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SetCheckInterval(interval: number): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/sub/interval'), location.href)

  return fetch(url, {
    headers: getCommonHeader(),
    method: 'PATCH',
    body: JSON.stringify({ checkInterval: interval })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function GetUpdateLogs(params: {
  page: number
  pageSize?: number
}): Promise<CommonResponseWithPage<updateLog>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/sub/logs'), location.href)
  url.searchParams.set('page', String(params.page))
  if (params.pageSize) {
    url.searchParams.set('pageSize', String(params.pageSize))
  }

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
