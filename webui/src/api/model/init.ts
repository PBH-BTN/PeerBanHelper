import type { CreateDownloadRequest } from './downloader'

export interface InitReq {
  token: string
  downloader: CreateDownloadRequest
}
