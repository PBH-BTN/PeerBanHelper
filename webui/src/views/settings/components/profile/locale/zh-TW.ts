export default {
  'page.settings.tab.profile.save': '儲存',
  'page.settings.tab.profile.base.title': '全域配置',
  'page.settings.tab.profile.form.checkInterval': '檢查頻率',
  'page.settings.tab.profile.unit.ms': '毫秒',
  'page.settings.tab.profile.unit.s': '秒',
  'page.settings.tab.profile.unit.bytes': '位元組',
  'page.settings.tab.profile.form.banDuration': '使用全域封禁時間',
  'page.settings.tab.profile.form.ignoreAddress': '忽略地址',
  'page.settings.tab.profile.form.ignoreAddress.action': '按此查看',
  'page.settings.tab.profile.form.ignoreAddress.tooltip':
    '來自這些 IP 位址的 Peers 不會被 PBH 檢查，繞過所有檢查規則',
  'page.settings.tab.profile.module.title': '模組配置',
  'page.settings.tab.profile.module.enable': '啟用',

  'page.settings.tab.profile.formArray.emptyTips': '請填寫非空字串',

  'page.settings.tab.profile.module.peerIdBlackList.if': '如果',
  'page.settings.tab.profile.module.peerIdBlackList.hit': '命中',
  'page.settings.tab.profile.module.peerIdBlackList.miss': '未命中',

  'page.settings.tab.profile.module.banRuleTips.STARTS_WITH': '匹配開頭',
  'page.settings.tab.profile.module.banRuleTips.ENDS_WITH': '匹配結尾',
  'page.settings.tab.profile.module.banRuleTips.LENGTH': '匹配字串長度',
  'page.settings.tab.profile.module.banRuleTips.CONTAINS': '匹配包含',
  'page.settings.tab.profile.module.banRuleTips.EQUALS': '匹配相同',
  'page.settings.tab.profile.module.banRuleTips.REGEX': '匹配正規表示式',

  'page.settings.tab.profile.module.banRuleTips.DEFAULT': '預設',
  'page.settings.tab.profile.module.banRuleTips.TRUE': '封禁',
  'page.settings.tab.profile.module.banRuleTips.FALSE': '排除',

  'page.settings.tab.profile.module.banRuleTips.empty': '存在為空的條目',

  'page.settings.tab.profile.module.peerIdBlackList': 'PeerID 封禁',
  'page.settings.tab.profile.module.peerIdBlackList.useGlobalBanTime': '使用全域封禁時間',
  'page.settings.tab.profile.module.peerIdBlackList.banPeerId': '封禁 PeerID',
  'page.settings.tab.profile.module.peerIdBlackList.rule': '規則',

  'page.settings.tab.profile.module.clientNameBlackList': '客戶端名稱封禁',
  'page.settings.tab.profile.module.clientNameBlackList.useGlobalBanTime': '使用全域封禁時間',
  'page.settings.tab.profile.module.clientNameBlackList.banClientName': '封禁客戶端名稱',
  'page.settings.tab.profile.module.clientNameBlackList.placeholder': '客戶端名稱',

  'page.settings.tab.profile.module.progressCheatBlocker': '進度作弊檢查',
  'page.settings.tab.profile.module.progressCheatBlocker.tips':
    '有時這會錯誤的封禁部分啟用「超級做種」的客戶端。但在大多數情況下，此模組能夠有效阻止循環下載的流量消耗器，建議啟用。',
  'page.settings.tab.profile.module.progressCheatBlocker.minSize': '最小檔案大小',
  'page.settings.tab.profile.module.progressCheatBlocker.minSize.tips':
    'Torrent 小於此值不進行檢查，對等體可能來不及同步正確的下載進度',
  'page.settings.tab.profile.module.progressCheatBlocker.maxDifference': '最大進度差異',
  'page.settings.tab.profile.module.progressCheatBlocker.maxDifference.tips':
    'PeerBanHelper 根據 BT 客戶端記錄的向此對等體實際上傳的位元組數，計算該對等體的最小下載進度，並與對等體匯報給 BT 客戶端下載進度進行比較，如果對等體匯報的總體下載進度遠遠低於我們上傳給此對等體的資料量的比例，我們應考慮客戶端正在匯報假進度，對於自動識別迅雷、QQ旋風的變種非常有效，能夠在不更新規則的情況下自動封禁報假進度的吸血客戶端',
  'page.settings.tab.profile.module.progressCheatBlocker.progressRewindDetection': '進度倒退檢測',
  'page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference': '最大回退進度差異',
  'page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference.tips':
    '考慮到有時文件片段在傳輸時可能因損壞而未通過校驗被丟棄，我們允許客戶端出現合理的進度倒退',
  'page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients': '禁止過量下載',
  'page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients.tips':
    '禁止那些在同一個種子的累計下載量超過種子本身大小的客戶端',
  'page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold': '過量下載閾值',
  'page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold.tips':
    '計算規則： 是否過量下載 = 上傳總大小 > (種子總大小 * excessive_threshold)',
  'page.settings.tab.profile.module.progressCheatBlocker.ipv4prefixlength': 'IPv4 前綴長度',
  'page.settings.tab.profile.module.progressCheatBlocker.ipv6prefixlength': 'IPv6 前綴長度',
  'page.settings.tab.profile.module.progressCheatBlocker.ipprefixLength.tips':
    '來自同一子網的 IP 視為同一使用者',
  'page.settings.tab.profile.module.progressCheatBlocker.banDuration': '封禁持續時間',
  'page.settings.tab.profile.module.progressCheatBlocker.enablePersist': '啟用持久化記錄',
  'page.settings.tab.profile.module.progressCheatBlocker.enablePersist.tips':
    '啟用此功能可能增加磁碟 I/O 並可能影響性能，嵌入式裝置上甚至可能帶來快閃記憶體磨損',
  'page.settings.tab.profile.module.progressCheatBlocker.persistDuration': '持久化時間',
  'page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration': '封禁前等待時間',
  'page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration.tips':
    '有時由於下載器網路原因，Peer 可能無法及時同步其進度資訊，當 Peer 達到封禁閾值後開始計時，如果 Peer 未在給定時間內更新自己的進度到正常水平，則將被封禁',
  'page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest': '啟用快速 PCB 測試',
  'page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest.tips':
    '此選項將允許 PCB 在 Peer 下載指定量的資料後，將其短暫的封禁一段時間以便斷開其連接，這有助於快速預熱進度重設檢查',
  'page.settings.tab.profile.module.progressCheatBlocker.fastPCBTestPercentage':
    '快速 PCB 測試啟動閾值',

  'page.settings.tab.profile.module.ipAddressBlocker.title': 'IP 位址封禁',
  'page.settings.tab.profile.module.ipAddressBlocker.useGlobalBanTime': '使用全域封禁時間',
  'page.settings.tab.profile.module.ipAddressBlocker.rules': '配置規則請前往{link}頁面',
  'page.settings.tab.profile.module.ipAddressBlocker.rules.link': '規則配置',

  'page.settings.tab.profile.module.autoRangeBan.title': '連鎖封禁',
  'page.settings.tab.profile.module.autoRangeBan.tips':
    '在封禁 Peer 後，被封禁的 Peer 所在 IP 位址的指定前綴長度內的其它 IP 位址都將一同連鎖封禁',
  'page.settings.tab.profile.module.autoRangeBan.useGlobalBanTime': '使用全域封禁時間',
  'page.settings.tab.profile.module.autoRangeBan.ipv4Prefix': 'IPv4 前綴長度',
  'page.settings.tab.profile.module.autoRangeBan.ipv6Prefix': 'IPv6 前綴長度',

  'page.settings.tab.profile.module.btn.enable.tips':
    '啟用來自 BTN 網路的規則，僅在 config.yml 中配置了 BTN 服務器時生效',

  'page.settings.tab.profile.module.multiDialingBlocker.title': '多撥封禁',
  'page.settings.tab.profile.module.multiDialingBlocker.useGlobalBanTime': '使用全域封禁時間',

  'page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-length': '子網掩碼長度',
  'page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-v6-length': 'IPv6子網掩碼長度',
  'page.settings.tab.profile.module.multiDialingBlocker.tolerate-num': '封禁閾值（{version}）',
  'page.settings.tab.profile.module.multiDialingBlocker.tolerate-num.tips':
    '容許同一網段下載同一種子的IP數量，防止DHCP重新分配IP、碰巧有同一小區的使用者下載同一種子等導致的誤判',
  'page.settings.tab.profile.module.multiDialingBlocker.timeWindow': '檢測視窗',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting': '是否追獵',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting.tips':
    '如果某IP已判定為多撥，無視快取時間限制繼續搜尋其同夥',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting-time': '追獵時間',

  'page.settings.tab.profile.module.expressionEngine.title': 'AviatorScript 規則引擎',
  'page.settings.tab.profile.module.expressionEngine.tips':
    '啟用此功能可以使用 AviatorScript 語言編寫複雜的規則，以實現更多的自訂功能',
  'page.settings.tab.profile.module.expressionEngine.useGlobalBanTime': '使用全域封禁時間',

  'page.settings.tab.profile.module.ruleSubscribe.title': '規則訂閱',
  'page.settings.tab.profile.module.ruleSubscribe.enable.tips':
    '啟用來自 BTN 網路的規則，僅在 config.yml 中配置了 BTN 服務器時生效',
  'page.settings.tab.profile.module.ruleSubscribe.useGlobalBanTime': '使用全域封禁時間',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe': '其他配置請前往{link}頁面',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe.link': '規則訂閱',

  'page.settings.tab.profile.module.activeMonitor.title': '主動監控',
  'page.settings.tab.profile.module.activeMonitor.enable.tips':
    '此功能允許 PeerBanHelper 監視下載器的網路傳輸活動，自動調整其傳輸速率設定，並在達到閾值時發送告警訊息',
  'page.settings.tab.profile.module.activeMonitor.disable.tips': '關閉後部分圖表功能不可用',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.enable': '開啟流量告警',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.tips':
    '設定流量告警閾值，當超出閾值後將發送告警資訊提醒您檢查下載器狀態',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.value':
    '每日流量告警閾值',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.enable':
    '開啟流量滑動視窗限制',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.enable.tips':
    '啟用此功能後，將接管所有下載器的上傳速率控制設定項',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.daily_max_allowed_upload_traffic':
    '滑動視窗最大上傳流量',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.daily_max_allowed_upload_traffic.tips':
    '滑動視窗區域內，最多允許上傳的流量',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed':
    '最小速度',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed.tips':
    '調整上傳速率時，最小允許的下載速率',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed.warning':
    '如果設定了最小速度，上述每日最大允許上傳流量可能不會被遵守',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.max_speed':
    '最大速度',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.max_speed.tips':
    '調整上傳速率時，最大允許的下載速率',
  'page.settings.tab.profile.module.idleConnectionDosProtection.title': '拒絕服務攻擊保護',
  'page.settings.tab.profile.module.idleConnectionDosProtection.enable.tips':
    '此模塊保護與 PeerBanHelper 關聯的下載器免受拒絕服務攻擊',
  'page.settings.tab.profile.module.idleConnectionDosProtection.useGlobalBanTime':
    '使用全域封禁時間',
  'page.settings.tab.profile.module.idleConnectionDosProtection.maxAllowedIdleTime':
    '允許的最長閒置連線時間',
  'page.settings.tab.profile.module.idleConnectionDosProtection.maxAllowedIdleTime.tips':
    '當一個 Peer 長時間沒有任何資料傳輸或狀態更新，且速度持續低於閒置速度閾值時，將被視為閒置連線',
  'page.settings.tab.profile.module.idleConnectionDosProtection.idleSpeedThreshold': '閒置速度閾值',
  'page.settings.tab.profile.module.idleConnectionDosProtection.idleSpeedThreshold.tips':
    '持續低於此速度將被視為閒置連線',
  'page.settings.tab.profile.module.idleConnectionDosProtection.minStatusChangePercentage':
    '狀態更新最小變化百分比',
  'page.settings.tab.profile.module.idleConnectionDosProtection.minStatusChangePercentage.tips':
    '狀態更新時，變化百分比低於此值時仍視為閒置',
  'page.settings.tab.profile.module.idleConnectionDosProtection.resetOnStatusChange':
    '狀態更新時重置計時器',
  'page.settings.tab.profile.module.idleConnectionDosProtection.resetOnStatusChange.tips':
    '當狀態更新時視為連線活動，重置計時器（如任務進度）',

  'page.settings.tab.profile.module.peerAnalyseService.title': 'Peer 統計與分析服務',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.title': '會話統計與分析服務',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.enable.tips':
    '記錄在統計週期內的 Peer 狀態資料，生成統計資料報表',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataFlushInterval':
    '資料刷寫間隔時間',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataFlushInterval.tips':
    '記錄在統計週期內的 Peer 狀態資料到資料庫的時間間隔',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.cleanupInterval':
    '資料匯總和清理間隔時間',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.cleanupInterval.tips':
    '匯總統計資料和清理過期資料的時間間隔',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataRetentionTime':
    '舊資料輪轉刪除時間',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataRetentionTime.tips':
    '超過此時間的歷史會話資料將被自動刪除',
  'page.settings.tab.profile.module.peerAnalyseService.swarmTracking.title': '使用者群追蹤服務',
  'page.settings.tab.profile.module.peerAnalyseService.swarmTracking.tips':
    '為 BTN 種群追蹤和其它類似所需該資料的功能提供資料支援，並追蹤本次運作會話期間的對等體資料，在重啟後資料將自動刪除',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.title': '對等體追蹤記錄服務',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.enable.tips':
    '持續追蹤和記錄 Peer 的狀態、會話、傳輸、偏移量等資料，為按 IP、Torrent 聚合資料分析和圖表生成、BTN 跨使用者 Peer 追蹤能力提供資料支援',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataFlushInterval':
    '資料刷寫間隔時間',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataFlushInterval.tips':
    '將 Peer 狀態、會話、傳輸、偏移量等資料寫入資料庫的時間間隔',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataRetentionTime':
    '資料保留時間',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataRetentionTime.tips':
    '持續追蹤記錄的 Peer 資料保留時長，超過此時間的資料將被刪除',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataCleanupInterval':
    '資料清理間隔時間',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataCleanupInterval.tips':
    '執行資料清理任務的時間間隔，建議不要設定得太頻繁以避免影響效能'
}
