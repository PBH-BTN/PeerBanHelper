export enum OSType {
  Linux = 'Linux',
  Windows = 'Windows',
  MacOS = 'Mac OS X',
  FreeBSD = 'FreeBSD',
  Solaris = 'Solaris',
  Other = 'Other'
}
interface JVMMemoryInfo {
  init: number
  used: number
  committed: number
  max: number
  free: number
}
export type RunningInfo = {
  jvm: {
    version: string
    vendor: string
    runtime: string
    bitness: 32 | 64
    memory: {
      heap: JVMMemoryInfo
      non_heap: JVMMemoryInfo
    }
  }
  system: {
    os: OSType
    version: string
    architecture: string
    cores: number
    memory: {
      total: number
      free: number
    }
    load: number
    network: {
      internet_access: boolean
      use_proxy: boolean
      reverse_proxy: boolean
      client_ip: string
    }
  }
  peerbanhelper: {
    version: string
    commit_id: string
    compile_time: number
    release: string
    uptime: number
  }
}

export interface BTNStatus {
  /**
   * 能力列表
   */
  abilities: Ability[]
  /**
   * AppID
   */
  appId: string
  /**
   * AppSecret (已脱敏)
   */
  appSecret: string
  /**
   * 配置文件是否成功获取且有效
   */
  configSuccess: boolean
  /**
   * BTN 配置文件获取 URL
   */
  configUrl: string
  configResult: string
}

export interface Ability {
  /**
   * 能力描述
   */
  description: string
  /**
   * 能力显示名称
   */
  displayName: string
  /**
   * 能力最后状态信息
   */
  lastMessage: string
  /**
   * 能力最后操作是否成功
   */
  lastSuccess: boolean
  /**
   * 能力最后状态更新时间
   */
  lastUpdateAt: number
  /**
   * 能力名称
   */
  name: string
}
