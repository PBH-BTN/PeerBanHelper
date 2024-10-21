enum OSType {
  Linux = 'Linux',
  Windows = 'Windows',
  MacOS = 'MacOS',
  FreeBSD = 'FreeBSD',
  Solaris = 'Solaris',
  Other = 'Other'
}
export type RunningInfo = {
  jvm: {
    version: string
    vendor: string
    runtime: string
    bitness: 32 | 64
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
    load: string
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
