import serviceLocale from '@/service/locale/en-US'
import banListPageLocale from '@/views/banlist/locale/en-US'
import chartsLocale from '@/views/charts/locale/en-US'
import scriptLocale from '@/views/custom-script/locale/en-US'
import dashboardPageLocale from '@/views/dashboard/locale/en-US'
import dataLocale from '@/views/data-view/locale/en-US'
import loginLocale from '@/views/login/locale/en-US'
import oobeLocale from '@/views/oobe/locale/en-US'
import topBanPageLocale from '@/views/ranks/locale/en-US'
import ruleManageMentLocale from '@/views/rule-management/locale/en-US'
import configLocale from '@/views/settings/locale/en-US'
import alertLocale from './en-US/alert'
import copierLocale from './en-US/copier'
import plusLocale from './en-US/plus'
import settingsLocale from './en-US/settings'

export default {
  'navbar.action.locale': 'Switch to English',
  'navbar.action.autoUpdate': 'Auto Update',
  'navbar.action.autoUpdate.lastUpdate': 'Last updated at: ',
  'main.workInProgressTips':
    'This feature is still working in progress, and the data currently recorded and displayed is relatively limited',
  'footer.newVersion': 'New Version Found!',
  'footer.newVersion.body': '{version} is available, click to view',
  'footer.newVersionTips': 'New version {version} is available, click to view',
  'footer.newVersion.updateNow': 'View Details',

  'changeLogModel.title': '🎉 New Version Found: {0}!',
  'changeLogModel.changelog': 'Change Log',
  'changeLogModel.notNow': 'Not Now',
  'changeLogModel.updateNow': 'Update Now',
  'globalPauseModel.title': 'Are you sure to enable global pause mode?',
  'globalPauseModel.description':
    'This will pause all ban and check operations of all downloaders until the next restart or cancel pause. IPs in banlist will be unbanned immediately.',
  'global.pause.pauseAll': 'Pause',
  'global.pause.pauseAll.tips':
    "This will stop PeerBanhelper's all functions and unban all Peers, suitable for modifying downloader configuration",
  'global.pause.pauseAll.result': 'Global pause mode has been started',
  'global.pause.pauseAll.stop': 'Global pause mode has been stopped',
  'global.pause.alert':
    'Global pause mode has been enabled, all ban and check operations have been disabled',
  'global.pause.alert.disable': 'Click to disable',

  'router.login': 'Login',
  'router.dashboard': 'Status',
  'router.banlist': 'Ban List',
  'router.rank': 'Ranks',
  'router.data': 'Data',
  'router.data.banlogs': 'Ban Logs',
  'router.data.torrent': 'Torrents',
  'router.data.ipHistory': 'IP Query',
  'router.metrics': 'Metrics',
  'router.metrics.ruleMetrics': 'Rule Metrics',
  'router.metrics.charts': 'Charts',
  'router.rule_management': 'Rule',
  'router.config': 'Preferences',
  'router.script': 'Custom Script',

  'router.moduleNotEnable': '{moduleName} is not enabled',
  'router.moduleNotEnable.tips': 'Please enable the feature in the configuration file',
  'router.moduleNotEnable.viewDoc': 'View Documentation',

  'service.networkErrorRetry': 'Network failure, will retry in {time}.',
  'service.networkErrorRetry.loading': 'Network failure, try to reloading...',
  'service.networkErrorRetry.second': '1 second | {count} seconds',
  'service.networkErrorRetry.cancel': 'Cancel Retry',
  'service.networkErrorRetry.retry': 'Retry',

  'queryLink.title': 'Click to query this IP',

  ...settingsLocale,
  ...plusLocale,
  ...dashboardPageLocale,
  ...banListPageLocale,
  ...dataLocale,
  ...topBanPageLocale,
  ...serviceLocale,
  ...loginLocale,
  ...oobeLocale,
  ...ruleManageMentLocale,
  ...chartsLocale,
  ...configLocale,
  ...alertLocale,
  ...copierLocale,
  ...scriptLocale
}
