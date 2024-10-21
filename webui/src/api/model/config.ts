export interface ConfigSaveResult {
  /**
   * 重载异常信息
   */
  errorMsg: string
  /**
   * 重载项名称
   */
  name: string
  /**
   * 产生此重载结果的原因
   */
  reason: string
  /**
   * 重载结果
   */
  result: Result
}

/**
 * 重载结果
 */
export enum Result {
  Exception = 'EXCEPTION',
  Outdated = 'OUTDATED',
  RequireRestart = 'REQUIRE_RESTART',
  Scheduled = 'SCHEDULED',
  Success = 'SUCCESS'
}
