export type ruleType = 'ip' | 'port' | 'asn' | 'region' | 'city' | 'nettype'
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
export type nettype = (typeof netTypeArray)[number]

export type BlackList<T extends ruleType> = {
  [K in T]: K extends 'nettype' ? nettype[] : K extends 'port' | 'asn' ? number[] : string[]
}
