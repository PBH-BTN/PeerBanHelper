export type NATType =
  | 'UdpBlocked'
  | 'OpenInternet'
  | 'SymmetricUdpFirewall'
  | 'FullCone'
  | 'RestrictedCone'
  | 'PortRestrictedCone'
  | 'Symmetric'
  | 'Unknown'

export interface DownloaderBasicInfo {
  id: string
  name: string
  type: string
}

export interface AutoSTUNStatus {
  enabled: boolean
  useFriendlyLoopbackMapping: boolean
  selectedDownloaders: DownloaderBasicInfo[]
  natType: NATType
}

export interface AutoSTUNConfig {
  enabled: boolean
  useFriendlyLoopbackMapping: boolean
  downloaders: string[]
}

export interface TunnelInfo {
  valid: boolean
  startedAt: number
  lastSuccessHeartbeatAt: number
  connectionsHandled: number
  connectionsFailed: number
  connectionsBlocked: number
  totalToDownstreamBytes: number
  totalToUpstreamBytes: number
  establishedConnections: number
  proxyHost: string
  proxyPort: number
  upstreamHost: string
  upstreamPort: number
}

export interface TunnelData {
  downloader: DownloaderBasicInfo
  tunnel: TunnelInfo
}

export interface ConnectionInfo {
  downstreamHost: string
  downstreamPort: number
  proxyHost: string
  proxyPort: number
  proxyOutgoingHost: string
  proxyOutgoingPort: number
  upstreamHost: string
  upstreamPort: number
  establishedAt: number
  lastActivityAt: number
  toDownstreamBytes: number
  toUpstreamBytes: number
}

export interface BackgroundTaskInfo {
  id: string
  showLogs: boolean
}
