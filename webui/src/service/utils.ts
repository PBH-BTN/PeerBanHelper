import { getNavigatorLanguage } from '@/locale'
import { useEndpointStore } from '@/stores/endpoint'

export function getCommonHeader(withToken = true): Headers {
  const { authToken } = useEndpointStore()
  // Accept-Language
  const lang = document.querySelector('html')?.getAttribute('lang') || getNavigatorLanguage()
  const headers = new Headers()
  headers.set('Accept-Language', lang)
  if (withToken) headers.set('Authorization', `Bearer ${authToken}`)
  return headers
}
