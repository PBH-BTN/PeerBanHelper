export interface version {
  version: string
  os: string
  branch: string
  commit: string
  abbrev: string
}

export interface release {
  tagName: string
  url: string
}

export interface module {
  className: string
  configName: string
}

export interface mainfest {
  version: version
  modules: module[]
}

export interface donateStatus {
  activated: boolean
  key: string
  keyData?: KeyData
}

export interface KeyData {
  createAt: number
  description: string
  expireAt: number
  hidden: string
  licenseTo: string
  source: string
  verifyMagic: string
}
