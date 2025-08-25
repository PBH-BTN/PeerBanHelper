export const ruleTypes = ['ip', 'port', 'asn', 'region', 'city', 'netType'] as const
export type ruleType = (typeof ruleTypes)[number]
export const netTypeArray = [
  'wideband',
  'baseStation',
  'governmentAndEnterpriseLine',
  'businessPlatform',
  'backboneNetwork',
  'ipPrivateNetwork',
  'internetCafe',
  'iot',
  'datacenter'
] as const
export type netType = (typeof netTypeArray)[number]

export type BlackList<T extends ruleType> = {
  [K in T]: K extends 'netType' ? netType[] : K extends 'port' | 'asn' ? number[] : string[]
}
