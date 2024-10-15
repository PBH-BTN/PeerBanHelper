import type { CommonResponse } from '@/api/model/common'
import type { Config } from '@/api/model/settings'
import sampleConfig from './sampleConfig.json'

export async function GetSettings(): Promise<CommonResponse<Config>> {
  return {
    success: true,
    message: 'success',
    data: sampleConfig
  }
}
