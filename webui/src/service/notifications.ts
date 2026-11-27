import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export interface NotificationRule {
  category: { id: string; name: string; group: string; keepHistory: boolean }
  useDefault: boolean
  channelIds: string[]
}

export interface NotificationSettings {
  channels: { id: string; name: string }[]
  rules: NotificationRule[]
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, path), location.href)
  const response = await fetch(url, { ...options, headers: getCommonHeader() })
  endpointStore.assertResponseLogin(response)
  return response.json()
}

export function getNotificationSettings(): Promise<CommonResponse<NotificationSettings>> {
  return request('api/notifications/settings')
}

export function saveNotificationRule(rule: NotificationRule): Promise<CommonResponseWithoutData> {
  return request(`api/notifications/routes/${encodeURIComponent(rule.category.id)}`, {
    method: 'PUT',
    body: JSON.stringify({ useDefault: rule.useDefault, channelIds: rule.channelIds })
  })
}
