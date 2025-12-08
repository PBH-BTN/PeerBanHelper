import type {downloaderConfig} from './downloader'

interface initDownloaderConfig {
  id: string
  config: downloaderConfig
}

export interface BtnConfig {
  mode: 'disabled' | 'anonymous' | 'account'
  appId: string
  appSecret: string
}

export interface InitConfig {
  acceptPrivacy: boolean
  token: string
  btnConfig: BtnConfig
  downloaderConfig: initDownloaderConfig
  valid: boolean
  btn: BtnConfig
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
