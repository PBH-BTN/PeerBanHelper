import type { Log } from '@/api/model/log'
import { WebSocketHandler } from './websocket'
import type { CommonResponse } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
export class StreamLogger extends WebSocketHandler<Log> {
  constructor() {
    const endpointStore = useEndpointStore()
    super(endpointStore.endpoint, 'api/logs/stream', endpointStore.authToken)
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
