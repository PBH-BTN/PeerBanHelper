import type { BTNIPCommentPage, BTNIPLookup } from '@/api/model/btn'
import type { CommonResponse } from '@/api/model/common'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

async function request(path: string, params: Record<string, string>, init?: RequestInit) {
  const endpoint = useEndpointStore()
  await endpoint.serverAvailable
  const url = new URL(urlJoin(endpoint.endpoint, '/api/peer', path), location.href)
  url.search = new URLSearchParams(params).toString()
  const response = await fetch(url, { ...init, headers: getCommonHeader() })
  endpoint.assertResponseLogin(response)
  return response
}

async function readResult<T>(response: Response): Promise<T> {
  const result: CommonResponse<T> = await response.json()
  if (!response.ok || !result.success) {
    throw new Error(result.message || `HTTP ${response.status}`)
  }
  return result.data
}

export async function getBtnLookup(query: string) {
  const response = await request('btnLookup', { query })
  if (response.status === 404 || response.status === 501) return undefined
  return readResult<BTNIPLookup>(response)
}

export async function getBtnComments({
  scope,
  page,
  pageSize
}: {
  scope: string
  page: number
  pageSize: number
}) {
  const result = await readResult<BTNIPCommentPage>(
    await request('btnComments', { scope, page: String(page), pageSize: String(pageSize) })
  )
  return {
    ...result,
    records: result.records.filter(
      (comment) => comment.moderationStatus !== 'REJECTED' || comment.self === true
    )
  }
}

export async function createBtnComment(
  scope: string,
  content: string,
  parentId: number | null = null
) {
  const response = await request(
    'btnComments',
    {},
    {
      method: 'POST',
      body: JSON.stringify({ scope, content, parentId })
    }
  )
  await readResult(response)
}

export async function deleteBtnComment(id: number) {
  await readResult<null>(await request(`btnComments/${id}`, {}, { method: 'DELETE' }))
}
