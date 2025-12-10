import type { CreateDownloadRequest } from './downloader'
import type { BtnConfig } from './oobe'

export interface InitReq {
  token: string
  downloader: CreateDownloadRequest
  btn: BtnConfig
}
