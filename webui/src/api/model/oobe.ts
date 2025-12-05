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
}
