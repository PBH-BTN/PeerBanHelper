export interface BanList {
  address: string
  banMetadata: BanMetadata
}

export interface BanMetadata {
  context: string
  randomId: string
  banAt: number
  unbanAt: number
  torrent: Torrent
  peer: Peer
  rule: string
  description: string
  reverseLookup: string
  geo?: IPGeoData
}

export interface Torrent {
  id: string
  size: number
  name: string
  hash: string
}

export interface Peer {
  address: Address
  id: string
  clientName: string
  downloaded: number
  uploaded: number
  progress: number
}

export interface Address {
  port: number
  ip: string
}

export interface IPGeoData {
  /**
   * 自治系统信息
   */
  as?: As
  /**
   * 城市/位置信息
   */
  city?: City
  /**
   * 国家/地区信息
   */
  country?: Country
  /**
   * 网络属性信息（由 GeoCN 提供）
   */
  network?: IPGeoDataNetwork
}

/**
 * 自治系统信息
 */
export interface As {
  /**
   * 网络地址
   */
  ipAddress?: string
  /**
   * 自治系统网络信息
   */
  network?: AsNetwork
  /**
   * 自治系统代码
   */
  number?: number
  /**
   * 自治系统所属组织
   */
  organization?: string
}

/**
 * 自治系统网络信息
 */
export interface AsNetwork {
  /**
   * 网络地址信息
   */
  ipAddress?: string
  /**
   * 前缀长度
   */
  prefixLength?: number
}

/**
 * 城市/位置信息
 */
export interface City {
  /**
   * 城市 ISO 代码/唯一识别代码
   */
  iso?: number
  /**
   * 位置信息
   */
  location?: Location
  /**
   * 城市名称
   */
  name?: string
}

/**
 * 位置信息
 */
export interface Location {
  /**
   * 精确半径
   */
  accuracyRadius?: number
  /**
   * 维度
   */
  latitude?: number
  /**
   * 精度
   */
  longitude?: number
  /**
   * 时区
   */
  timeZone?: string
}

/**
 * 国家/地区信息
 */
export interface Country {
  /**
   * ISO代码
   */
  iso?: string
  /**
   * 国家/地区名称
   */
  name?: string
}

/**
 * 网络属性信息（由 GeoCN 提供）
 */
export interface IPGeoDataNetwork {
  /**
   * 服务提供商信息
   */
  isp?: string
  /**
   * 网络类型，常见值如下，这些数据使用中文在 MMDB 中被硬编码，非后端翻译：
   * 宽带
   * 基站
   * 政企专线
   * 业务平台
   * 骨干网
   * IP专网
   * 网吧
   * 物联网
   * 数据中心
   * （可能增加）
   */
  netType?: string
}

export interface UnbanResult {
  count?: number
}
