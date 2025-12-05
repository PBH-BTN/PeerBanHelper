import type {CreateDownloadRequest} from './downloader'

export interface BtnInitConfig {
  enabled: boolean
  submit: boolean
  app_id?: string | null
  app_secret?: string | null
}

export interface InitReq {
  token: string
  downloader: CreateDownloadRequest
  btn?: BtnInitConfig
}
