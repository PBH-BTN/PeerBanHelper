export interface LicenseManifest {
  /**
   * 当前所有处于有效状态的许可证的可用的所有功能列表，各个功能特性参见接口说明
   */
  enabledFeatures: string[]
  licenses: License[]
}

export type License = LicenseV1 | LicenseV2

export interface LicenseV1 {
  /**
   * 许可证元数据，可能是下面的其中一个，具体是哪个由 version 指示
   */
  data: LicenseDataV1
  /**
   * 明文许可证内容
   */
  licenseId: string
  /**
   * 许可证状态
   */
  status: LicenseStatus
  /**
   * 许可证版本号
   */
  version: 1
}

export interface LicenseV2 {
  /**
   * 许可证元数据，可能是下面的其中一个，具体是哪个由 version 指示
   */
  data: LicenseDataV2
  /**
   * 明文许可证内容
   */
  licenseId: string
  /**
   * 许可证状态
   */
  status: LicenseStatus
  /**
   * 许可证版本号
   */
  version: 2
}

export interface LicenseDataV1 {
  createAt: number
  description: string
  expireAt: number
  hidden: string
  licenseTo: string
  source: string
  verifyMagic: string
  type: LicenseType
}

/**
 * 许可证元数据，可能是下面的其中一个，具体是哪个由 version 指示
 *
 * 许可证版本1
 *
 * V1License
 *
 * 许可证版本2
 *
 * V2License
 */
export interface LicenseDataV2 {
  /**
   * 签发时间毫秒时间戳
   */
  createAt: number
  /**
   * 描述，爱发电为订单 ID，其它情况按需填写
   */
  description: null | string
  /**
   * 过期时间毫秒时间戳
   */
  expireAt: number
  /**
   * 许可给，爱发电为用户ID，其它情况填写目标用户人类可读名称
   */
  licenseTo: string
  /**
   * 许可证签发者，如：PBH-ALIS，或签发人人类可读名称
   */
  source: string
  /**
   * 许可证发布类型，afdian/mbd/local
   *
   * 许可证发布类型，afdian 或 mbd
   */
  type: LicenseType
  /**
   * 电子邮件地址，购买时填写的电子邮件地址；可能为空（如本地许可证）
   */
  email?: null | string
  /**
   * 许可证包含的功能列表
   */
  features?: string[]
  /**
   * 许可证版本号
   */
  licenseVersion?: number
  /**
   * 平台订单号，可能为空（如本地许可证）
   */
  orderId?: null | string
  /**
   * 购买时实际支付的金额，用户支付的金额
   */
  paid?: number
  /**
   * 支付网关类型，以实际情况为准，如 alipay, wechatpay, mastercard, visa, paypal..... 等；可能为空（如本地许可证）
   */
  paymentGateway?: null | string
  /**
   * 支付网关订单号，也称为商家订单号；可能为空（如本地许可证）
   */
  paymentOrderId?: null | string
  /**
   * 购买时选择的 SKU，不同平台可能不一样，以实际情况为准；可能为空（如本地许可证）
   */
  sku?: null | string
  /**
   * 许可证起效毫秒时间戳
   */
  startAt?: number
}

/**
 * 许可证发布类型，afdian/mbd/local
 *
 * 许可证发布类型，afdian 或 mbd
 */
export enum LicenseType {
  Afdian = 'afdian',
  Local = 'local',
  Mbd = 'mbd'
}

/**
 * 许可证状态
 */
export enum LicenseStatus {
  Expired = 'EXPIRED',
  Invalid = 'INVALID',
  NotStarted = 'NOT_STARTED',
  Revoked = 'REVOKED',
  Valid = 'VALID'
}

export interface FreeLicenseChallenge {
  /**
   * 验证码挑战哈希摘要算法
   */
  algorithm: string
  /**
   * 验证码挑战内容，Base64 编码的 byte[]
   */
  challengeBase64: string
  /**
   * 验证码挑战ID
   */
  challengeId: string
  /**
   * 验证码挑战难度（leading bits of zero）
   */
  difficultyBits: number
}
