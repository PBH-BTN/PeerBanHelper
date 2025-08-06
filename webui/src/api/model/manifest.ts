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
  changeLog: string
}

export interface module {
  className: string
  configName: string
}

export interface mainfest {
  version: version
  modules: module[]
}
export interface GlobalConfig {
  globalPaused: boolean
}
