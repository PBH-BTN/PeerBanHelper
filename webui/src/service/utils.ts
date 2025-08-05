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
