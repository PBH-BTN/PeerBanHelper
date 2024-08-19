export interface ruleBrief {
  enabled: boolean
  entCount: number
  lastUpdate: number
  ruleId: string
  ruleName: string
  subUrl: string
}

export enum updateType {
  AUTO = 'AUTO',
  MANUAL = 'MANUAL'
}

export interface updateLog {
  count: number
  ruleId: string
  updateTime: number
  updateType: updateType
}
