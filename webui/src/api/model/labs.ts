export interface Experiment {
  /**
   * 实验是否已激活
   */
  activated?: boolean
  /**
   * 实验描述(Markdown)
   */
  description?: string
  /**
   * 实验ID
   */
  id?: string
  /**
   * 实验标题
   */
  title?: string
}
