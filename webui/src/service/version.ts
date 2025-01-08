import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { donateStatus, GlobalConfig, mainfest } from '@/api/model/manifest'
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

export async function getPBHPlusStatus(): Promise<CommonResponse<donateStatus>> {
  const endpointStore = useEndpointStore()
  await endpointStore.serverAvailable
  const url = new URL(urlJoin(endpointStore.endpoint, '/api/pbhplus/status'), location.href)
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

export function obtainFreeTrial(): Promise<CommonResponseWithoutData> {
  const url = new URL(
    urlJoin(useEndpointStore().endpoint, 'api/pbhplus/renewFreeLicense'),
    location.href
  )
  return fetch(url, {
    method: 'POST',
    headers: getCommonHeader()
  }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}

/**
 * Retrieves the manifest metadata from the server.
 *
 * @remarks
 * Fetches the manifest from the specified endpoint, with error handling for network and parsing issues.
 * Validates the manifest structure to ensure it contains a valid modules array and version object.
 *
 * @param endpoint - The base URL endpoint for the API, defaults to the current endpoint from the store
 * @returns A promise resolving to the manifest metadata
 *
 * @throws {GetManifestError} If there are network connection issues, JSON parsing errors, or invalid manifest format
 *
 * @example
 * ```typescript
 * try {
 *   const manifest = await getManifest();
 *   console.log(manifest.modules);
 *   console.log(manifest.version);
 * } catch (error) {
 *   if (error instanceof GetManifestError) {
 *     // Handle manifest retrieval errors
 *   }
 * }
 * ```
 */
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

/**
 * Retrieves the global configuration from the server.
 * 
 * @returns A promise resolving to the global configuration response
 * @throws Will throw an error if the server response indicates a login issue or network problem
 * 
 * @remarks
 * This function fetches the global configuration by making a GET request to the global configuration API endpoint.
 * It uses the current endpoint from the endpoint store and includes common headers with the request.
 * 
 * @example
 * const globalConfig = await GetGlobalConfig();
 * console.log(globalConfig.data); // Access the global configuration data
 */
export function GetGlobalConfig(): Promise<CommonResponse<GlobalConfig>> {
  const url = new URL(urlJoin(useEndpointStore().endpoint, 'api/general/global'), location.href)
  return fetch(url, { headers: getCommonHeader() }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}

/**
 * Updates the global configuration for the application.
 * 
 * @param config - The global configuration object to be updated
 * @returns A promise resolving to the response from the server after updating the configuration
 * 
 * @remarks
 * Sends a PATCH request to the global configuration endpoint with the provided configuration.
 * Validates the user's login status before processing the request.
 * 
 * @throws Will throw an error if the request fails or the user is not logged in
 */
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
