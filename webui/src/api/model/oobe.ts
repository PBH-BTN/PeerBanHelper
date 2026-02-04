import type { InitReq } from './init'

export interface InitConfig extends InitReq {
  acceptPrivacy: boolean
  downloaderValid: boolean
  databaseValid: boolean
}

export interface BtnConfig {
  app_id: null | string
  app_secret: null | string
  enabled: boolean
  submit: boolean
}

export interface OobeStepConfig {
  titleKey: string
  descriptionKey?: string
  hidden?: boolean
  canNext?: (config: InitConfig) => boolean
  component: () => Promise<unknown>
}
