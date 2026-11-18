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

export interface BTNCommentAccess {
  visible: boolean
}

export interface BTNIPLookup {
  lookup: {
    activity: BTNIPQuery | null
    commentAccess?: BTNCommentAccess
  }
}

export interface BTNIPComment {
  id: number
  parentId: number | null
  network: string
  authorNickname: string | null
  content: string
  createdAt: string
  moderationStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'FAILED'
  moderationReason: string | null
  self: boolean
  canDelete: boolean
}

export interface BTNIPCommentPage {
  records: BTNIPComment[]
  current: number
  size: number
  total: number
  scope: string
  access?: BTNCommentAccess
}
