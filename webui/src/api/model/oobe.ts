import type { downloaderConfig } from './downloader'

interface initDownloaderConfig {
  name: string
  config: downloaderConfig
}
export interface InitConfig {
  acceptPrivacy: boolean
  token: string
  downloaderConfig: initDownloaderConfig
  valid: boolean
}
