import settingsLocale from './en-US/settings'
import plusLocale from './en-US/plus'
import dashboardPageLocale from '@/views/dashboard/locale/en-US'
import banListPageLocale from '@/views/banlist/locale/en-US'
import banLogPageLocale from '@/views/banlog/locale/en-US'
import topBanPageLocale from '@/views/ranks/locale/en-US'
import ruleMetricsLocale from '@/views/rule-metrics/locale/en-US'
import serviceLocale from '@/service/locale/en-US'
import loginLocale from '@/views/login/locale/en-US'
import oobeLocale from '@/views/oobe/locale/en-US'
import ruleManageMentLocale from '@/views/rule-management/locale/en-US'
import chartsLocale from '@/views/charts/locale/en-US'
export default {
  'navbar.action.locale': 'Switch to English',
  'navbar.action.autoUpdate': 'Auto Update',
  'navbar.action.autoUpdate.lastUpdate': 'Last updated at: ',
  'main.workInProgressTips':
    'This feature is still working in progress, and the data currently recorded and displayed is relatively limited',
  'footer.newVersion': 'New Version Found!',
  'footer.newVersion.body': '{version} is available, click to view',
  'footer.newVersionTips': 'New version {version} is available, click to view',
  'footer.newVersion.updateNow': 'Update Now',

  'router.login': 'Login',
  'router.dashboard': 'Status',
  'router.banlist': 'Ban List',
  'router.banlogs': 'Ban Logs',
  'router.rank': 'Ranks',
  'router.metrics': 'Metrics',
  'router.metrics.ruleMetrics': 'Rule Metrics',
  'router.metrics.charts': 'Charts',
  'router.rule_management': 'Rule Management',

  'router.moduleNotEnable': '{moduleName} is not enabled',
  'router.moduleNotEnable.tips': 'Please enable the feature in the configuration file',
  'router.moduleNotEnable.viewDoc': 'View Documentation',

  'service.networkErrorRetry': 'Network failure, will retry in {time}.',
  'service.networkErrorRetry.loading': 'Network failure, try to reloading...',
  'service.networkErrorRetry.second': '1 second | {count} seconds',
  'service.networkErrorRetry.cancel': 'Cancel Retry',
  'service.networkErrorRetry.retry': 'Retry',

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
