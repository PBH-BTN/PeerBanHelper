export interface Script {
  /**
   * 脚本作者
   */
  author?: string
  /**
   * 运行结果是否可缓存
   */
  cacheable?: boolean
  /**
   * 脚本ID
   */
  id: string
  /**
   * 脚本名称
   */
  name?: string
  /**
   * 是否线程安全
   */
  threadSafe?: boolean
  /**
   * 脚本版本号
   */
  version: string
}

export interface EditableResult {
  reason: string
  editable: boolean
}
