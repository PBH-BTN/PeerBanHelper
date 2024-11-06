export interface Config {
  config_version: number
  language: 'en_us' | 'zh_cn' | 'default'
  pbh_plus_key: string
  server: Server
  logger: Logger
  lookup: Lookup
  persist: Persist
  btn: Btn
  banlist_invoker: BanlistInvoker
  ip_database: IpDatabase
  proxy: Proxy
  privacy: Privacy
  performance: Performance
}

export interface Server {
  http: number
  address: string
  prefix: string
  token: string
  allow_cors: boolean
}

export interface Logger {
  hide_finish_log: boolean
}

export interface Lookup {
  dns_reverse_lookup: boolean
}

export interface Persist {
  ban_logs_keep_days: number
  banlist: boolean
}

export interface Btn {
  enabled: boolean
  submit: boolean
  app_id: string
  app_secret: string
  config_url: string
  allow_script_execute: boolean
}

export interface BanlistInvoker {
  ipfilter_dat: IpfilterDat
  command_exec: CommandExec
}

export interface IpfilterDat {
  enabled: boolean
}

export interface CommandExec {
  enabled: boolean
  reset: string[]
  ban: string[]
  unban: string[]
}

export interface IpDatabase {
  auto_update: boolean
  database_city: string
  database_asn: string
}
export enum ProxySetting {
  NO_PROXY = 0,
  SYSTEM_PROXY = 1,
  HTTP_PROXY = 2,
  SOCKS_PROXY = 3
}

export interface Proxy {
  setting: ProxySetting
  host?: string
  port?: number
  non_proxy_hosts?: string // non-proxy hosts, split by |
}

export interface Privacy {
  error_reporting: boolean
}

export interface Performance {
  windows_ecoqos_api: boolean
}
