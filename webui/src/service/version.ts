import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'
import { Octokit } from '@octokit/core'
import type { donateStatus, mainfest } from '@/api/model/manifest'
import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
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

export function getPBHPlusStatus(): Promise<CommonResponse<donateStatus>> {
  const url = new URL(urlJoin(useEndpointStore().endpoint, '/api/pbhplus/status'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}

export function setPHBPlusKey(key: string): Promise<CommonResponseWithoutData> {
  const url = new URL(urlJoin(useEndpointStore().endpoint, '/api/pbhplus/key'), location.href)
  return fetch(url, {
    method: 'PUT',
    headers: getCommonHeader(),
    body: JSON.stringify({ key })
  }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}

export function getManifest(endpoint = useEndpointStore().endpoint): Promise<mainfest> {
  const url = new URL(urlJoin(endpoint, '/api/metadata/manifest'), location.href)
  return (
    fetch(url, { headers: getCommonHeader(false) })
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
