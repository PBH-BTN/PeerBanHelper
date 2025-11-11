import { getNavigatorLanguage } from '@/locale'
import { useEndpointStore } from '@/stores/endpoint'
import dayjs from 'dayjs'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'

export function getCommonHeader(): Headers {
  // Accept-Language
  const lang = document.querySelector('html')?.getAttribute('lang') || getNavigatorLanguage()
  const headers = new Headers()
  headers.set('Accept-Language', lang)
  headers.set('Content-Type', 'application/json')

  // X-TimeZone
  dayjs.extend(utc)
  dayjs.extend(timezone)
  headers.set('X-TimeZone', dayjs.tz.guess())
  const { authToken } = useEndpointStore()

  headers.set('Authorization', `Bearer ${authToken}`)

  return headers
}

/**
 * 添加排序参数到 URL
 * Add sorting parameters to URL
 * 支持多条件排序，格式: field1|dir1&field2|dir2
 * Supports multi-column sorting, format: field1|dir1&field2|dir2
 *
 * 后端使用多个 orderBy 查询参数来接收多个排序条件
 * Backend uses multiple orderBy query parameters to receive multiple sort conditions
 * 例如: ?orderBy=field1|asc&orderBy=field2|desc
 * Example: ?orderBy=field1|asc&orderBy=field2|desc
 */
export function appendSorterToUrl(url: URL, sorter: string) {
  const sortConditions = sorter.split('&')
  sortConditions.forEach((condition) => {
    url.searchParams.append('orderBy', condition)
  })
}
