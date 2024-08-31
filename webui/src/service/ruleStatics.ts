import type { GetRuleMetricsResponse } from '@/api/model/ruleStatics'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
import type { CommonResponse } from '@/api/model/common'

export async function getRuleStatic(): Promise<CommonResponse<GetRuleMetricsResponse>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/statistic/rules'), location.href)

  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}
