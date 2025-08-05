import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import { type FreeLicenseChallenge, type LicenseManifest } from '@/api/model/plus'
import { useEndpointStore } from '@/stores/endpoint'
import urlJoin from 'url-join'
import { getCommonHeader } from './utils'

export async function getPBHPlusStatus(): Promise<CommonResponse<LicenseManifest>> {
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

export function deletePHBPlusKey(licenseId: string): Promise<CommonResponseWithoutData> {
  const url = new URL(urlJoin(useEndpointStore().endpoint, '/api/pbhplus/key'), location.href)
  return fetch(url, {
    method: 'DELETE',
    headers: getCommonHeader(),
    body: JSON.stringify({ licenseId })
  }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}

export async function obtainFreeTrial(): Promise<CommonResponseWithoutData> {
  try {
    // 1. 获取验证码挑战
    const challengeResponse = await claimRenewFreeLicenseCaptcha()
    if (!challengeResponse.success || !challengeResponse.data) {
      throw new Error('Failed to get captcha challenge')
    }

    const challenge = challengeResponse.data

    // 2. 解码Base64挑战内容
    const challengeBytes = Array.from(
      new Uint8Array(Uint8Array.from(atob(challenge.challengeBase64), (c) => c.charCodeAt(0)))
    )

    // 3. 解决PoW挑战
    const nonce = await solvePoWChallenge(
      challengeBytes,
      challenge.difficultyBits,
      challenge.algorithm
    )

    // 4. 将nonce转换为Base64字符串
    const nonceBase64 = btoa(String.fromCharCode(...nonce))

    // 5. 提交验证结果
    const url = new URL(
      urlJoin(useEndpointStore().endpoint, 'api/pbhplus/renewFreeLicense'),
      location.href
    )

    return fetch(url, {
      method: 'POST',
      headers: getCommonHeader(),
      body: JSON.stringify({
        captchaId: challenge.challengeId,
        captchaNonce: nonceBase64
      })
    }).then((res) => {
      useEndpointStore().assertResponseLogin(res)
      return res.json()
    })
  } catch (error) {
    console.error('Failed to obtain free trial:', error)
    throw error
  }
}

export function claimRenewFreeLicenseCaptcha(): Promise<CommonResponse<FreeLicenseChallenge>> {
  const url = new URL(
    urlJoin(useEndpointStore().endpoint, 'api/pbhplus/claimRenewFreeLicenseCaptcha'),
    location.href
  )
  return fetch(url, {
    method: 'GET',
    headers: getCommonHeader()
  }).then((res) => {
    useEndpointStore().assertResponseLogin(res)
    return res.json()
  })
}

/**
 * 解决PoW挑战 - 客户端计算满足难度要求的nonce
 */
export async function solvePoWChallenge(
  challengeBytes: number[],
  difficultyBits: number,
  algorithm: string = 'SHA-256'
): Promise<number[]> {
  const challenge = new Uint8Array(challengeBytes)
  const maxIterations = 10000000 // 防止无限循环

  for (let nonce = 0; nonce < maxIterations; nonce++) {
    // 将nonce转换为字节数组
    const nonceBytes = new Uint8Array(4)
    const view = new DataView(nonceBytes.buffer)
    view.setUint32(0, nonce, false) // big-endian

    // 组合challenge和nonce
    const combined = new Uint8Array(challenge.length + nonceBytes.length)
    combined.set(challenge, 0)
    combined.set(nonceBytes, challenge.length)

    try {
      // 计算哈希
      const hashBuffer = await crypto.subtle.digest(algorithm, combined)
      const hash = new Uint8Array(hashBuffer)

      // 检查是否满足难度要求
      if (hasLeadingZeroBits(hash, difficultyBits)) {
        return Array.from(nonceBytes)
      }
    } catch (error) {
      console.error('Error computing hash:', error)
      throw new Error('Failed to compute PoW solution')
    }
  }

  throw new Error('Unable to find PoW solution within maximum iterations')
}

/**
 * 检查哈希是否有足够的前导零位
 */
function hasLeadingZeroBits(hash: Uint8Array, bits: number): boolean {
  const fullBytes = Math.floor(bits / 8)
  const remainingBits = bits % 8

  // 检查完整的字节
  for (let i = 0; i < fullBytes; i++) {
    if (hash[i] !== 0) return false
  }

  // 检查剩余的位
  if (remainingBits > 0) {
    const mask = 0xff << (8 - remainingBits)
    return (hash[fullBytes] & mask) === 0
  }

  return true
}
