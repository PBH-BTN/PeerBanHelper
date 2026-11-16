export interface BTNBanRecord {
  populate_time: number
  torrent: string
  peer_ip: string
  peer_port: number
  peer_id?: string | null
  peer_client_name?: string | null
  peer_progress: number
  peer_flags?: string | null
  reporter_progress: number
  to_peer_traffic: number
  from_peer_traffic: number
  module_name: string
  rule: string
  description: string
  structured_data?: Record<string, unknown> | null
}

export interface BTNBans {
  duration: number
  total: number
  records: BTNBanRecord[]
}

export interface BTNSwarmRecord {
  torrent: string
  peer_ip: string
  peer_port: number
  peer_id?: string | null
  peer_client_name?: string | null
  peer_progress: number
  from_peer_traffic: number
  to_peer_traffic: number
  from_peer_traffic_offset: number
  to_peer_traffic_offset: number
  flags?: string | null
  first_time_seen: number
  last_time_seen: number
  user_progress: number
}

export interface BTNSwarms {
  duration: number
  total: number
  records: BTNSwarmRecord[]
  concurrent_download_torrents_count: number
  concurrent_seeding_torrents_count: number
}

export interface BTNTraffic {
  duration: number
  to_peer_traffic: number
  from_peer_traffic: number
  share_ratio: number
}

export interface BTNTorrents {
  duration: number
  count: number
}

export interface BTNIPQuery {
  color: string
  labels: string[]
  bans: BTNBans | null
  swarms: BTNSwarms | null
  traffic: BTNTraffic | null
  torrents: BTNTorrents | null
}

export interface BTNIPGeo {
  country: string | null
  province: string | null
  city: string | null
  district: string | null
  asn: string | null
  asnOrganization: string | null
  asnNetwork: string | null
  isp: string | null
  networkType: string | null
}

export interface BTNCommentAccess {
  visible: boolean
  publishingEnabled: boolean
  moderationEnabled: boolean
  maxLength: number
  hourlyLimit: number
}

export interface BTNIPLookup {
  canComment: boolean
  lookup: {
    normalizedAddress: string
    canonicalCidr: string
    rangeStart: string
    rangeEnd: string
    totalAddresses: string
    expandedAddress: string
    binaryAddress: string
    decimalAddress: string | null
    reverseDnsName: string
    flags: string[]
    globallyRoutable: boolean
    geoStart: BTNIPGeo | null
    geoEnd: BTNIPGeo | null
    activity: BTNIPQuery
    defaultCommentScope: string
    commentScopes: { prefix: number; scope: string }[]
    commentAccess: BTNCommentAccess
  }
}

export interface BTNIPComment {
  id: number
  parentId: number | null
  network: string
  authorNickname: string | null
  content: string
  revision: number
  createdAt: string
  moderationStatus: string
  moderationReason: string | null
  canEdit: boolean
  canDelete: boolean
  history: { revision: number; content: string; createdAt: string }[]
}

export interface BTNIPCommentPage {
  records: BTNIPComment[]
  current: number
  size: number
  total: number
  scope: string
  access: BTNCommentAccess
}
