import type { downloaderConfig } from './downloader'

interface initDownloaderConfig {
  name: string
  config: downloaderConfig
}
export interface InitConfig {
  token: string
  downloaderConfig: initDownloaderConfig
  valid: boolean
}
