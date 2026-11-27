import type { CommonResponse } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export type UpdateMode = 'MANUAL' | 'AUTOMATIC' | 'DISABLED'
export interface UpdateStatus {
  mode: UpdateMode
  deployment: string
  canInstall: boolean
  currentVersion: string
  state: string
  release?: {
    id: string
    version: string
    url: string
    changeLog?: string
    size?: number
    installable: boolean
  }
}

export async function requestUpdateStatus(path = '', method = 'GET', body?: object) {
  const endpoint = useEndpointStore()
  await endpoint.serverAvailable
  const response = await fetch(
    new URL(urlJoin(endpoint.endpoint, 'api/updates', path), location.href),
    {
      method,
      headers: getCommonHeader(),
      body: body ? JSON.stringify(body) : undefined
    }
  )
  endpoint.assertResponseLogin(response)
  const result: CommonResponse<UpdateStatus> = await response.json()
  if (!response.ok || !result.success) throw new Error(result.message || String(response.status))
  return result.data
}
