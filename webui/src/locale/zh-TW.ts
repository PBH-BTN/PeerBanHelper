import serviceLocale from '@/service/locale/zh-TW'
import banListPageLocale from '@/views/banlist/locale/zh-TW'
import chartsLocale from '@/views/charts/locale/zh-TW'
import scriptLocale from '@/views/custom-script/locale/zh-TW'
import dashboardPageLocale from '@/views/dashboard/locale/zh-TW'
import dataLocale from '@/views/data-view/locale/zh-TW'
import loginLocale from '@/views/login/locale/zh-TW'
import oobeLocale from '@/views/oobe/locale/zh-TW'
import topBanPageLocale from '@/views/ranks/locale/zh-TW'
import ruleManageMentLocale from '@/views/rule-management/locale/zh-TW'
import configLocale from '@/views/settings/locale/zh-TW'
import alertLocale from './zh-TW/alert'
import copierLocale from './zh-TW/copier'
import plusLocale from './zh-TW/plus'
import routerLocale from './zh-TW/router'
import settingsLocale from './zh-TW/settings'

export default {
  'navbar.action.locale': '切換為繁體中文',
  'navbar.action.autoUpdate': '自動重新整理',
  'navbar.action.globalPause': '全域暫停',
  'navbar.action.globalPause.description':
    '這將暫停所有下載器的封禁和檢查操作，直至下次重啟或者取消暫停。',
  'navbar.action.autoUpdate.lastUpdate': '最後更新於：',
  'main.workInProgressTips': '請注意，此功能仍在施工中，目前記錄和展示的資料較為有限。',
  'footer.newVersion': '發現新版本！',
  'footer.newVersion.body': '{version} 已發布，按此查看',
  'footer.newVersionTips': '有新版本 {version} 可用，按此查看',
  'footer.newVersion.updateNow': '查看詳情',

  'changeLogModel.title': '🎉 發現新版本：{0}！',
  'changeLogModel.changelog': '更新日誌',
  'changeLogModel.notNow': '不是現在',
  'changeLogModel.updateNow': '立即更新',
  'globalPauseModel.title': '確定啟用全域暫停模式嗎？',
  'globalPauseModel.description':
    '這將暫停所有下載器的封禁和檢查操作，直至下次重啟或者取消暫停。已封禁的 IP 位址將全部立刻解除封禁。',
  'global.pause.pauseAll': '暫停',
  'global.pause.pauseAll.tips':
    '這將停止 PeerBanhelper 的全部功能並解封全部 Peer，適用於修改下載器配置等場景',
  'global.pause.pauseAll.result': '全域暫停模式已啟動',
  'global.pause.pauseAll.stop': '全域暫停模式已停止',
  'global.pause.alert': '全域暫停模式已啟動，所有檢查和封禁操作均已暫停',
  'global.pause.alert.disable': '按此關閉',

  'router.login': '登入',
  'router.dashboard': '狀態',
  'router.banlist': '封禁名單',
  'router.data': '資料透視',
  'router.data.banlogs': '封禁日誌',
  'router.data.torrent': '種子',
  'router.data.ipHistory': 'IP 查詢',
  'router.rank': '排行',
  'router.metrics': '統計',
  'router.metrics.ruleMetrics': '規則統計',
  'router.metrics.charts': '圖表',
  'router.rule_management': '規則管理',
  'router.config': '設定',
  'router.script': '自訂腳本',

  'router.moduleNotEnable': '{moduleName}功能未啟用',
  'router.moduleNotEnable.tips': '請在設定檔中開啟相關功能',
  'router.moduleNotEnable.viewDoc': '查看文件',

  'service.networkErrorRetry': '網路連線失敗，將於{time}後重試',
  'service.networkErrorRetry.loading': '網路連線失敗，正在重新連接',
  'service.networkErrorRetry.second': '{count}秒',
  'service.networkErrorRetry.cancel': '取消重試',
  'service.networkErrorRetry.retry': '重試',

  'queryLink.title': '按此查詢 IP',

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
  ...scriptLocale,
  ...routerLocale
}
