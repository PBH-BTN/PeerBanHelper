import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { GlobalConfig, mainfest } from '@/api/model/manifest'
import { useEndpointStore } from '@/stores/endpoint'
import { Octokit } from '@octokit/core'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
export class GetManifestError extends Error {
  static name = 'GetManifestError' as const
  name = GetManifestError.name
  constructor(
    message: string,
    public isApiWrong = true,
    public isManual = false
  ) {
    super(message)
  }

  static is(err: unknown): err is GetManifestError {
    return (err as Error)?.name === GetManifestError.name
  }
}

export function getLatestVersion(token = useEndpointStore().accessToken) {
  const octokit = new Octokit({ auth: token })
  return octokit
    .request('GET /repos/{owner}/{repo}/releases/latest', {
      owner: 'PBH-BTN',
      repo: 'PeerBanHelper',
      headers: {
        'X-GitHub-Api-Version': '2022-11-28'
      }
    })
    .then((res) => res.data)
}

export function getManifest(endpoint = useEndpointStore().endpoint): Promise<mainfest> {
  const url = new URL(urlJoin(endpoint, '/api/metadata/manifest'), location.href)
  return (
    fetch(url, { headers: getCommonHeader() })
      .catch(() => {
        throw new GetManifestError('service.manifest.networkError', false)
      })
      .then((res) =>
        res.json().catch(() => {
          throw new GetManifestError('service.manifest.parseError')
        })
      )
      .then((res: CommonResponse<mainfest>) => res.data)
      // 后续可以添加后端版本的校验和提醒
      .then((res: mainfest) => {
        if (!Array.isArray(res.modules) || typeof res.version !== 'object') {
          throw new GetManifestError('service.manifest.formatError')
        }
        return res
      })
  )
}

export async function GetGlobalConfig(): Promise<CommonResponse<GlobalConfig>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable

  const url = new URL(urlJoin(useEndpointStore().endpoint, 'api/general/global'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    endpointStore.assertResponseLogin(res)
    return res.json()
  })
}

export function UpdateGlobalConfig(config: GlobalConfig): Promise<CommonResponseWithoutData> {
  const url = new URL(urlJoin(useEndpointStore().endpoint, 'api/general/global'), location.href)
  return fetch(url, {
    method: 'PATCH',
    body: JSON.stringify(config),
    headers: getCommonHeader()
  }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}
