import settingsLocale from './zh-CN/settings'
import plusLocale from './zh-CN/plus'
import dashboardPageLocale from '@/views/dashboard/locale/zh-CN'
import banListPageLocale from '@/views/banlist/locale/zh-CN'
import banLogPageLocale from '@/views/banlog/locale/zh-CN'
import topBanPageLocale from '@/views/ranks/locale/zh-CN'
import ruleMetricsLocale from '@/views/rule-metrics/locale/zh-CN'
import serviceLocale from '@/service/locale/zh-CN'
import loginLocale from '@/views/login/locale/zh-CN'
import oobeLocale from '@/views/oobe/locale/zh-CN'
import ruleManageMentLocale from '@/views/rule-management/locale/zh-CN'
import chartsLocale from '@/views/charts/locale/zh-CN'
export default {
  'navbar.action.locale': '切换为中文',
  'navbar.action.autoUpdate': '自动刷新',
  'navbar.action.autoUpdate.lastUpdate': '最后更新于：',
  'main.workInProgressTips': '请注意，此功能仍在施工中，目前记录和展示的数据较为有限。',
  'footer.newVersion': '发现新版本！',
  'footer.newVersion.body': '{version} 已发布，点击查看',
  'footer.newVersionTips': '有新版本 {version} 可用，点击查看',
  'footer.newVersion.updateNow': '立即更新',

  'router.login': '登录',
  'router.dashboard': '状态',
  'router.banlist': '封禁名单',
  'router.banlogs': '封禁日志',
  'router.rank': '排行',
  'router.metrics': '统计',
  'router.metrics.ruleMetrics': '规则统计',
  'router.metrics.charts': '图表',
  'router.rule_management': '规则管理',

  'router.moduleNotEnable': '{moduleName}功能未启用',
  'router.moduleNotEnable.tips': '请在配置文件中开启相关功能',
  'router.moduleNotEnable.viewDoc': '查看文档',

  'service.networkErrorRetry': '网络连接失败，将于{time}后重试',
  'service.networkErrorRetry.loading': '网络连接失败，正在重新连接',
  'service.networkErrorRetry.second': '{count}秒',
  'service.networkErrorRetry.cancel': '取消重试',
  'service.networkErrorRetry.retry': '重试',

  ...settingsLocale,
  ...plusLocale,
  ...dashboardPageLocale,
  ...banListPageLocale,
  ...banLogPageLocale,
  ...topBanPageLocale,
  ...ruleMetricsLocale,
  ...serviceLocale,
  ...loginLocale,
  ...oobeLocale,
  ...ruleManageMentLocale,
  ...chartsLocale
}
