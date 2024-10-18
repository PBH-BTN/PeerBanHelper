import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { Config } from '@/api/model/config'
import type { Profile } from '@/api/model/profile'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function GetProfile(): Promise<CommonResponse<Profile>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(endpointStore.endpoint, 'api/config/profile.yml'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SaveProfile(config: Profile): Promise<CommonResponseWithoutData> {
  console.log(config)
  return new Promise((res) => {
    setTimeout(() => {
      res({
        success: true,
        message: 'success'
      })
    }, 3000)
  })
}

export async function GetConfig(): Promise<CommonResponse<Config>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(
    urlJoin(endpointStore.endpoint, 'api/config/config/profile.yml'),
    location.href
  )
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export async function SaveConfig(config: Config): Promise<CommonResponseWithoutData> {
  console.log(config)
  return new Promise((res) => {
    setTimeout(() => {
      res({
        success: true,
        message: 'success'
      })
    }, 3000)
  })
}
