import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { Config } from '@/api/model/settings'
import sampleConfig from './sampleConfig.json'

export async function GetConfig(): Promise<CommonResponse<Config>> {
  return new Promise((res) => {
    setTimeout(() => {
      res({
        success: true,
        message: 'success',
        data: sampleConfig
      })
    }, 3000)
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
