import type { InitReq } from './init'

export interface InitConfig extends InitReq {
  acceptPrivacy: boolean
  valid: boolean
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
  canNext?: (config: InitConfig) => boolean
  component: () => Promise<unknown>
}
