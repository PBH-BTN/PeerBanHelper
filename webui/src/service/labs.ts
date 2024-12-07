import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { Experiment } from '@/api/model/labs'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function GetExperimentList(): Promise<
  CommonResponse<{ labEnabled: boolean; experiments: Experiment[] }>
> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/laboratory/experiments'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SetExperimentStatus(
  id: string,
  status: boolean
): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, `api/laboratory/experiment/${id}`),
    location.href
  )
  return fetch(url, {
    method: 'PUT',
    headers: getCommonHeader(),
    body: JSON.stringify({ status })
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SetLabConfig(config: {
  enabled: boolean
}): Promise<CommonResponseWithoutData> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, `api/laboratory/config`), location.href)
  return fetch(url, {
    method: 'POST',
    headers: getCommonHeader(),
    body: JSON.stringify(config)
  }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
