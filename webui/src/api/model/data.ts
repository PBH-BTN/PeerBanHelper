import type { IPGeoData } from './banlist'
export interface TorrentInfo {
  id: number
  infoHash: string
  name: string
  size: number
  peerBanCount: number
  peerAccessCount: number
}

export interface AccessHistory {
  /**
   * IP 地址，可能为 IPv4 / IPv6
   */
  address: string
  /**
   * 客户端名称
   */
  clientName: string
  /**
   * 下载量，总和
   */
  downloaded: number
  /**
   * 最后一次下载量记录，偏移
   */
  downloadedOffset: number
  /**
   * 下载器名称
   */
  downloader: string
  /**
   * 首次发现于
   */
  firstTimeSeen: number
  /**
   * 记录主键ID
   */
  id: number
  /**
   * 最后一次记录的 UTP Flags
   */
  lastFlags: string
  /**
   * 最后一次见到于，每次发现此 Peer 时，将会更新上面记录的各类数据
   */
  lastTimeSeen: number
  /**
   * PeerID
   */
  peerId: string
  torrent: TorrentInfo
  /**
   * 上传量，总和
   */
  uploaded: number
  /**
   * 最后一次上传量记录，偏移
   */
  uploadedOffset: number
}

export interface IPBasicInfo {
  address: string
  banCount: number
  downloadedFromPeer: number
  firstTimeSeen: number
  /**
   * GeoIP 信息，可能为空
   */
  geo?: null | IPGeoData
  lastTimeSeen: number
  torrentAccessCount: number
  uploadedToPeer: number
}
