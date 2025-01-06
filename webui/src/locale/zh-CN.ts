import serviceLocale from '@/service/locale/zh-CN'
import banListPageLocale from '@/views/banlist/locale/zh-CN'
import chartsLocale from '@/views/charts/locale/zh-CN'
import scriptLocale from '@/views/custom-script/locale/zh-CN'
import dashboardPageLocale from '@/views/dashboard/locale/zh-CN'
import dataLocale from '@/views/data-view/locale/zh-CN'
import loginLocale from '@/views/login/locale/zh-CN'
import oobeLocale from '@/views/oobe/locale/zh-CN'
import topBanPageLocale from '@/views/ranks/locale/zh-CN'
import ruleManageMentLocale from '@/views/rule-management/locale/zh-CN'
import configLocale from '@/views/settings/locale/zh-CN'
import alertLocale from './zh-CN/alert'
import copierLocale from './zh-CN/copier'
import plusLocale from './zh-CN/plus'
import settingsLocale from './zh-CN/settings'

export default {
  'navbar.action.locale': '切换为中文',
  'navbar.action.autoUpdate': '自动刷新',
  'navbar.action.globalPause': '全局暂停',
  'navbar.action.globalPause.description':
    '这将暂停所有下载器的封禁和检查操作，直至下次重启或者取消暂停。',
  'navbar.action.autoUpdate.lastUpdate': '最后更新于：',
  'main.workInProgressTips': '请注意，此功能仍在施工中，目前记录和展示的数据较为有限。',
  'footer.newVersion': '发现新版本！',
  'footer.newVersion.body': '{version} 已发布，点击查看',
  'footer.newVersionTips': '有新版本 {version} 可用，点击查看',
  'footer.newVersion.updateNow': '查看详情',

  'changeLogModel.title': '🎉 发现新版本：{0}！',
  'changeLogModel.changelog': '更新日志',
  'changeLogModel.notNow': '不是现在',
  'changeLogModel.updateNow': '立即更新',
  'globalPauseModel.title': '确定启用全局暂停模式吗？',
  'globalPauseModel.description':
    '这将暂停所有下载器的封禁和检查操作，直至下次重启或者取消暂停。已封禁的 IP 地址将全部立刻解除封禁。',
  'global.pause.pauseAll': '暂停',
  'global.pause.pauseAll.tips':
    '这将停止 PeerBanhelper 的全部功能并解封全部 Peer，适用于修改下载器配置等场景',
  'global.pause.pauseAll.result': '全局暂停模式已启动',
  'global.pause.pauseAll.stop': '全局暂停模式已停止',
  'global.pause.alert': '全局暂停模式已启动，所有检查和封禁操作均已暂停',
  'global.pause.alert.disable': '点击关闭',

  'router.login': '登录',
  'router.dashboard': '状态',
  'router.banlist': '封禁名单',
  'router.data': '数据透视',
  'router.data.banlogs': '封禁日志',
  'router.data.torrent': '种子',
  'router.data.ipHistory': 'IP 查询',
  'router.rank': '排行',
  'router.metrics': '统计',
  'router.metrics.ruleMetrics': '规则统计',
  'router.metrics.charts': '图表',
  'router.rule_management': '规则管理',
  'router.config': '设置',
  'router.script': '自定义脚本',

  'router.moduleNotEnable': '{moduleName}功能未启用',
  'router.moduleNotEnable.tips': '请在配置文件中开启相关功能',
  'router.moduleNotEnable.viewDoc': '查看文档',

  'service.networkErrorRetry': '网络连接失败，将于{time}后重试',
  'service.networkErrorRetry.loading': '网络连接失败，正在重新连接',
  'service.networkErrorRetry.second': '{count}秒',
  'service.networkErrorRetry.cancel': '取消重试',
  'service.networkErrorRetry.retry': '重试',

  'queryLink.title': '点击查询 IP',

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
