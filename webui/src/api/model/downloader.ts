import type { IPGeoData } from './banlist'

export interface ClientStatus {
  activePeers: number
  activeTorrents: number
  lastStatus: ClientStatusEnum
  lastStatusMessage: string
  config: downloaderConfig
}

export enum ClientStatusEnum {
  HEALTHY = 'HEALTHY',
  ERROR = 'ERROR',
  UNKNOWN = 'UNKNOWN',
  NEED_TAKE_ACTION = 'NEED_TAKE_ACTION',
  PAUSED = 'PAUSED'
}

export enum ClientTypeEnum {
  qBittorrent = 'qbittorrent',
  qBittorrentEE = 'qbittorrentee',
  Transmission = 'transmission',
  BiglyBT = 'biglybt',
  Deluge = 'deluge',
  BitComet = 'bitcomet'
}

export interface ScanDownloaderInfo {
  /**
   * 主机名
   */
  host: string
  /**
   * 进程 PID
   */
  pid: number
  /**
   * 端口号
   */
  port: number
  /**
   * 下载器类型枚举
   */
  type: ClientTypeEnum
}

export interface Downloader {
  id: string
  name: string
  endpoint: string
  type: ClientStatusEnum
  paused: boolean
}

export interface DownloaderBasicInfo {
  id: string
  name: string
  type: ClientTypeEnum
}

export interface Torrent {
  id: string
  size: number
  completedSize: number
  name: string
  hash: string
  privateTorrent: boolean
  progress: number
  rtDownloadSpeed: number
  rtUploadSpeed: number
}

export interface PeerInfo {
  /**
   * Peer 的 IP 的 ASN 信息
   */
  asn: Asn
  /**
   * 下载器名称
   */
  downloader: string
  /**
   * Peer 地理位置信息
   */
  geo: IPGeoData
  /**
   * Peer 信息
   */
  peer: Peer
  /**
   * 随机 ID
   */
  randomId: string
  /**
   * Peer IP 反查结果，N/A 代表无结果或者查询被禁用
   */
  reverseLookup: string
  /**
   * 种子
   */
  torrent: TorrentWrapper
}

/**
 * Peer 的 IP 的 ASN 信息
 */
interface Asn {
  /**
   * AS号码
   */
  asn: number
  /**
   * AS IP段
   */
  asNetwork: string
  /**
   * AS所属组织
   */
  asOrganization: string
}

/**
 * Peer 信息
 */
interface Peer {
  /**
   * Peer 连接信息
   */
  address: Address
  /**
   * BitTorrent BEP Client Name (User-Agent)
   */
  clientName: string
  /**
   * 已下载字节数
   */
  downloaded: number
  /**
   * 下载速度（单位由下载器决定），单位通常为bytes/s
   */
  downloadSpeed: number
  /**
   * BitTorrent BEP 标志，https://github.com/PBH-BTN/quick-references/blob/main/utp_flags.md
   */
  flags: PeerFlags
  /**
   * BitTorrent BEP PeerID
   */
  id: string
  /**
   * 下载进度，0~1；1=100%
   */
  progress: number
  /**
   * 已上传字节数
   */
  uploaded: number
  /**
   * 上传速度（单位由下载器决定），单位通常为bytes/s
   */
  uploadSpeed: number
}

/**
 * Peer Flags
 */
interface PeerFlags {
  bitset: number
  /**
   * 标准 libTorrent 标志，不管下载器原始 Flags 如何，PBH 总是将其转换为 LT 的 Flags
   */
  ltStdString: string
}

/**
 * Peer 连接信息
 */
interface Address {
  /**
   * 连接 IP
   */
  ip: string
  /**
   * 连接端口
   */
  port: number
}

/**
 * 种子
 *
 * Torrent 信息
 */
interface TorrentWrapper {
  /**
   * 种子首选 info_hash，具体是 v1 的还是 v2 的由下载器决定
   */
  hash?: string
  /**
   * 该种子在下载器中的唯一识别符
   */
  id?: string
  /**
   * 种子名称
   */
  name?: string
  /**
   * 下载速率，单位 bytes
   */
  rtDownloadSpeed?: number
  /**
   * 上传速率，单位 bytes
   */
  rtUploadSpeed?: number
  /**
   * 种子大小（bytes）
   */
  size?: number

  completedSize?: number

  privateTorrent?: boolean

  progress?: number
}

export type downloaderConfig =
  | qBittorrentConfig
  | qBittorrentEEConfig
  | transmissionConfig
  | biglybtConfig
  | delugeConfig
  | bitCometConfig

export interface qBittorrentConfig {
  type: ClientTypeEnum.qBittorrent
  name: string
  endpoint: string
  username: string
  password: string
  basicAuth: BasicAuth
  httpVersion: string
  incrementBan: boolean
  verifySsl: boolean
  ignorePrivate: boolean
  paused: boolean
}

export interface qBittorrentEEConfig {
  type: ClientTypeEnum.qBittorrentEE
  name: string
  endpoint: string
  username: string
  password: string
  basicAuth: BasicAuth
  httpVersion: string
  incrementBan: boolean
  useShadowBan: boolean
  verifySsl: boolean
  ignorePrivate: boolean
  paused: boolean
}

interface BasicAuth {
  user: string
  pass: string
}

export interface transmissionConfig {
  type: ClientTypeEnum.Transmission
  name: string
  endpoint: string
  username: string
  password: string
  httpVersion: string
  verifySsl: boolean
  ignorePrivate: boolean
  paused: boolean
  rpcUrl: string
}

export interface biglybtConfig {
  type: ClientTypeEnum.BiglyBT
  name: string
  endpoint: string
  token: string
  httpVersion: string
  verifySsl: boolean
  ignorePrivate: boolean
  paused: boolean
}

export interface delugeConfig {
  type: ClientTypeEnum.Deluge
  name: string
  endpoint: string
  password: string
  httpVersion: string
  incrementBan: boolean
  verifySsl: boolean
  ignorePrivate: boolean
  paused: boolean
  rpcUrl: string
}

export interface bitCometConfig {
  type: ClientTypeEnum.BitComet
  name: string
  endpoint: string
  username: string
  password: string
  httpVersion: string
  incrementBan: boolean
  verifySsl: boolean
  ignorePrivate: boolean
  paused: boolean
}

export interface CreateDownloadRequest {
  id: string
  config: downloaderConfig
}
