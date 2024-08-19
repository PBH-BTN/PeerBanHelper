import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
import type { ruleType, BlackList } from '@/api/model/blacklist'
import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'

export async function getBlackList<T extends ruleType>(
  type: T
): Promise<CommonResponse<BlackList<T>>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `/api/modules/ipblacklist/${type}`),
    location.href
  )

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function addBlackList<T extends ruleType>(
  target: T extends 'port' | 'asn' ? number : string,
  type: T
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `/api/modules/ipblacklist/${type}`),
    location.href
  )

  return fetch(url, {
    method: 'PUT',
    headers: getCommonHeader(),
    body: JSON.stringify({ [type]: target })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function deleteBlackList<T extends ruleType>(
  target: T extends 'port' | 'asn' ? number : string,
  type: T
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `/api/modules/ipblacklist/${type}`),
    location.href
  )

  return fetch(url, {
    method: 'DELETE',
    headers: getCommonHeader(),
    body: JSON.stringify({ [type]: target })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
