import type { KV } from './common'

export interface Statistic {
  peerUnbanCounter: number
  peerBanCounter: number
  checkCounter: number
  banlistCounter: number
  bannedIpCounter: number
  wastedTraffic: number
  trackedSwarmCount: number
  peersBlockRate: number
  weeklySessions: number
}

export interface AnalysisField {
  data: string
  count: number
  percent: number
}

export interface TimeStatisticItem {
  timestamp: number
  count: number
  percent: number
}

export type BanTrends = KV<number, number>
export interface Trends {
  connectedPeersTrend: KV<number, number>[]
  bannedPeersTrend: KV<number, number>[]
}

export interface Traffic {
  timestamp: number
  dataOverallUploaded: number
  dataOverallDownloaded: number
}

export interface GeoIP {
  city: KV<string, number>[]
  isp: KV<string, number>[]
  province: KV<string, number>[]
  region: KV<string, number>[]
}

export interface SessionDayBucket {
  key: number
  totalConnections: number
  incomingConnections: number
  remoteRefuseTransferToClient: number
  remoteAcceptTransferToClient: number
  localRefuseTransferToPeer: number
  localAcceptTransferToPeer: number
  localNotInterested: number
  questionStatus: number
  optimisticUnchoke: number
  fromDHT: number
  fromPEX: number
  fromLSD: number
  fromTrackerOrOther: number
  rc4Encrypted: number
  plainTextEncrypted: number
  utpSocket: number
  tcpSocket: number
}
