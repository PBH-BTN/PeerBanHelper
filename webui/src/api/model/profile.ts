export interface Profile {
  config_version: number
  check_interval: number
  ban_duration: number
  ignore_peers_from_addresses: string[]
  module: Module
}

type BanDuration = number | 'default'

export interface Module {
  peer_id_blacklist: PeerIdBlacklist
  client_name_blacklist: ClientNameBlacklist
  progress_cheat_blocker: ProgressCheatBlocker
  ip_address_blocker: IpAddressBlocker
  auto_range_ban: AutoRangeBan
  btn: Btn
  multi_dialing_blocker: MultiDialingBlocker
  expression_engine: ExpressionEngine
  ip_address_blocker_rules: IpAddressBlockerRules
  active_monitoring: ActiveMonitoring
  ptr_blacklist: PtrBlacklist
}

export interface PeerIdBlacklist {
  enabled: boolean
  ban_duration: BanDuration
  banned_peer_id: PeerRule[]
}

export interface PeerRule {
  method: 'STARTS_WITH' | 'ENDS_WITH' | 'LENGTH' | 'CONTAINS' | 'EQUALS' | 'REGEX'
  content: string
  if?: PeerRule
  hit?: 'TRUE' | 'FALSE' | 'DEFAULT'
  miss?: 'TRUE' | 'FALSE' | 'DEFAULT'
}

export interface ClientNameBlacklist {
  enabled: boolean
  ban_duration: BanDuration
  banned_client_name: PeerRule[]
}

export interface ProgressCheatBlocker {
  enabled: boolean
  minimum_size: number
  maximum_difference: number
  rewind_maximum_difference: number
  block_excessive_clients: boolean
  excessive_threshold: number
  ipv4_prefix_length: number
  ipv6_prefix_length: number
  ban_duration: BanDuration
  enable_persist: boolean
  persist_duration: number
  max_wait_duration: number
  fast_pcb_test_percentage: number
  fast_pcb_test_block_duration: number
}

export interface IpAddressBlocker {
  enabled: boolean
  ban_duration: BanDuration
  ips: string[]
  ports: number[]
  asns: string[]
  regions: string[]
  cities: string[]
  net_type: NetType
}

export interface NetType {
  wideband: boolean
  base_station: boolean
  government_and_enterprise_line: boolean
  business_platform: boolean
  backbone_network: boolean
  ip_private_network: boolean
  internet_cafe: boolean
  iot: boolean
  datacenter: boolean
}

export interface AutoRangeBan {
  enabled: boolean
  ban_duration: BanDuration
  ipv4: number
  ipv6: number
}

export interface Btn {
  enabled: boolean
  ban_duration: BanDuration
}

export interface MultiDialingBlocker {
  enabled: boolean
  ban_duration: BanDuration
  subnet_mask_length: number
  subnet_mask_v6_length: number
  tolerate_num_ipv4: number
  tolerate_num_ipv6: number
  cache_lifespan: number
  keep_hunting: boolean
  keep_hunting_time: number
}

export interface ExpressionEngine {
  enabled: boolean
  ban_duration: BanDuration
}

export interface IpAddressBlockerRules {
  enabled: boolean
  ban_duration: BanDuration
  check_interval: number
  rules: Rules
}

export interface Rules {
  all_in_one: AllInOne
}

export interface AllInOne {
  enabled: boolean
  name: string
  url: string
}

export interface ActiveMonitoring {
  enabled: boolean
  data_retention_time: number
  data_cleanup_interval: number
  traffic_monitoring: {
    daily: number
  }
}

export interface PtrBlacklist {
  enabled: boolean
  ban_duration: BanDuration
  ptr_rules: PeerRule[]
}
