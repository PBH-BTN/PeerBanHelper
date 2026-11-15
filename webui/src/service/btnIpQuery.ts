import type { BTNIPComment, BTNIPCommentPage, BTNIPLookup } from '@/api/model/btn'
import type { CommonResponse } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

async function request<T>(
  path: string,
  params: Record<string, string> = {},
  init?: RequestInit
): Promise<CommonResponse<T> | undefined> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, '/api/peer', path), location.href)
  url.search = new URLSearchParams(params).toString()
  const response = await fetch(url, { ...init, headers: getCommonHeader() })
  endpointStore.assertResponseLogin(response)
  if (response.status === 404 || response.status === 501) return undefined
  if (response.status === 204) return { success: true, message: '', data: undefined as T }
  return response.json()
}

export function getBtnLookup(query: string) {
  return request<BTNIPLookup>('btnLookup', { query })
}

export function getBtnComments(scope: string, page: number) {
  return request<BTNIPCommentPage>('btnComments', { scope, page: String(page) })
}

export function saveBtnComment(
  content: string,
  scope: string,
  parentId: number | null,
  editing: BTNIPComment | null
) {
  return request<BTNIPComment>(
    editing ? 'btnComments/' + editing.id : 'btnComments',
    {},
    {
      method: editing ? 'PUT' : 'POST',
      body: JSON.stringify(
        editing ? { content, revision: editing.revision } : { content, scope, parentId }
      )
    }
  )
}

export function deleteBtnComment(id: number) {
  return request<void>('btnComments/' + id, {}, { method: 'DELETE' })
}
