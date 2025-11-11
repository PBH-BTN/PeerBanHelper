/**
 * 可复用的排序组合函数，用于处理表格的多条件排序
 * Reusable sorting composable for handling multi-column table sorting
 *
 * 基于后端 Orderable 类的设计，支持多条件排序
 * Based on backend Orderable class design, supports multi-column sorting
 */

import {computed, ref, type Ref} from 'vue'

/**
 * 排序状态接口
 * Sorting state interface
 */
export interface SortState {
  dataIndex: string
  direction: 'ascend' | 'descend'
}

/**
 * 排序配置接口
 * Sorting configuration interface
 */
export interface SorterOptions {
  /** 是否启用多条件排序，默认为 true */
  multiSort?: boolean
  /** 最大排序条件数量，默认为 3 */
  maxSortColumns?: number
}

/**
 * 使用排序功能
 * @param options 排序配置选项
 * @returns 排序相关的响应式状态和方法
 */
export function useSorter(options: SorterOptions = {}) {
  const { multiSort = true, maxSortColumns = 3 } = options

  // 存储多个排序条件，按照添加顺序排列（LinkedHashMap 的行为）
  // Store multiple sort conditions in order (LinkedHashMap behavior)
  const sortStates = ref<SortState[]>([])

  /**
   * 将排序状态转换为 API 查询参数
   * Convert sort states to API query parameter
   * 格式：dataIndex|direction (多个用 & 连接，例如: banAt|descend&peerUploaded|ascend)
   * Format: dataIndex|direction (multiple connected by &, e.g.: banAt|descend&peerUploaded|ascend)
   */
  const sorterParam = computed(() => {
    if (sortStates.value.length === 0) {
      return undefined
    }
    return sortStates.value.map((state) => `${state.dataIndex}|${state.direction}`).join('&')
  })

  /**
   * 处理表格排序变化事件
   * Handle table sorter change event
   * @param dataIndex 列数据索引
   * @param direction 排序方向
   */
  const handleSorterChange = (dataIndex: string, direction: 'ascend' | 'descend' | '') => {
    if (!direction) {
      // 清除该列的排序
      // Clear sorting for this column
      sortStates.value = sortStates.value.filter((s) => s.dataIndex !== dataIndex)
      return
    }

    const existingIndex = sortStates.value.findIndex((s) => s.dataIndex === dataIndex)

    if (existingIndex >= 0) {
      // 更新现有排序条件
      // Update existing sort condition
      sortStates.value[existingIndex].direction = direction
    } else {
      if (multiSort) {
        // 多条件排序模式：添加新的排序条件
        // Multi-sort mode: add new sort condition
        sortStates.value.push({ dataIndex, direction })

        // 如果超过最大数量，移除最早的排序条件
        // Remove oldest condition if exceeds max
        if (sortStates.value.length > maxSortColumns) {
          sortStates.value.shift()
        }
      } else {
        // 单条件排序模式：替换现有排序条件
        // Single-sort mode: replace existing sort condition
        sortStates.value = [{ dataIndex, direction }]
      }
    }
  }

  /**
   * 清除所有排序条件
   * Clear all sort conditions
   */
  const clearSorter = () => {
    sortStates.value = []
  }

  /**
   * 获取指定列的排序状态（用于表格显示排序图标）
   * Get sort state for specific column (for table sorting icon display)
   * @param dataIndex 列数据索引
   * @returns 排序方向或空字符串
   */
  const getSortOrder = (dataIndex: string): 'ascend' | 'descend' | '' => {
    const state = sortStates.value.find((s) => s.dataIndex === dataIndex)
    return state ? state.direction : ''
  }

  /**
   * 获取指定列的排序优先级（从 1 开始）
   * Get sort priority for specific column (starts from 1)
   * @param dataIndex 列数据索引
   * @returns 排序优先级或 undefined
   */
  const getSortPriority = (dataIndex: string): number | undefined => {
    const index = sortStates.value.findIndex((s) => s.dataIndex === dataIndex)
    return index >= 0 ? index + 1 : undefined
  }

  return {
    sortStates: sortStates as Ref<readonly SortState[]>,
    sorterParam,
    handleSorterChange,
    clearSorter,
    getSortOrder,
    getSortPriority
  }
}
