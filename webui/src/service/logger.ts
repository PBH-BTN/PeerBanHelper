import type { CommonResponse } from '@/api/model/common'
import type { Log } from '@/api/model/log'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { SSEHandler } from './eventstream'
import { getCommonHeader } from './utils'
export class StreamLogger extends SSEHandler<Log> {
  constructor() {
    const endpointStore = useEndpointStore()
    super(endpointStore.endpoint, 'api/logs/live', endpointStore.authToken)
  }
}

export async function GetHistoryLogs(): Promise<CommonResponse<Log[]>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/logs/history'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

