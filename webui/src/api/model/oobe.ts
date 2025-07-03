import type { downloaderConfig } from './downloader'

interface initDownloaderConfig {
  id: string
  config: downloaderConfig
}
export interface InitConfig {
  acceptPrivacy: boolean
  token: string
  downloaderConfig: initDownloaderConfig
  valid: boolean
}
