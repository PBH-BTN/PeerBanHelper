export interface BanLog {
  banAt: number
  unbanAt: Date
  peerIp: string
  peerPort: number
  peerId: string
  peerClientName: string
  peerUploaded: number
  peerDownloaded: number
  peerProgress: number
  torrentInfoHash: string
  torrentName: string
  torrentSize: number
  module: string
  description: string
}
