export interface Alert {
  /**
   * 警报内容，可以有多行，\n换行
   */
  content: string
  /**
   * 警报创建时间
   */
  createAt: number
  /**
   * 警报 ID
   */
  id: number
  /**
   * 事件识别符，处于未读状态的相同事件识别符只会最多存在一个。但一旦事件标记为已读，则可以继续插入新的。
   *
   * 此设计为避免对于相同问题同时创建多个重复事件。
   */
  identifier: string
  /**
   * 警报等级
   */
  level: Level
  /**
   * 警报已读时间，如果从未读过，则为 null
   */
  readAt: number | null
  /**
   * 警报标题
   */
  title: string
}

/**
 * 警报等级
 */
export enum Level {
  Error = 'ERROR',
  Fatal = 'FATAL',
  Info = 'INFO',
  Tip = 'TIP',
  Warn = 'WARN'
}
