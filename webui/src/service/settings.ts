import type { CommonResponse, CommonResponseWithoutData } from '@/api/model/common'
import type { Config } from '@/api/model/config'
import type { Profile } from '@/api/model/profile'
import sampleConfig from './sampleConfig.json'
import sampleProfile from './sampleProfile.json'

export async function GetProfile(): Promise<CommonResponse<Profile>> {
  return new Promise((res) => {
    setTimeout(() => {
      res({
        success: true,
        message: 'success',
        data: sampleProfile
      })
    }, 3000)
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
