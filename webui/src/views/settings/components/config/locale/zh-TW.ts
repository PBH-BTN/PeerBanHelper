export default {
  'page.settings.tab.config.title': '基礎設定',
  'page.settings.tab.config.tips': '這裡儲存了PBH執行必不可少的一些基礎設定',

  'page.settings.tab.config.unit.day': '天',

  'page.settings.tab.config.language': '語言',
  'page.settings.tab.config.language.default': '跟隨系統',
  'page.settings.tab.config.language.tips':
    '此處是後端程式的語言，包含返回訊息日誌等，這{not} WebUI 語言！',
  'page.settings.tab.config.language.tips.not': '不是',

  'page.settings.tab.config.plus.button': '按此打開',

  'page.settings.tab.config.privacy.errorReport': '啟用錯誤報告',

  'page.settings.tab.config.server.title': 'WebUI',
  'page.settings.tab.config.server.port': '埠',
  'page.settings.tab.config.server.port.error': '埠號必須在1-65535之間',
  'page.settings.tab.config.server.address': '地址',
  'page.settings.tab.config.server.prefix': '前綴',
  'page.settings.tab.config.server.prefix.tips':
    '當 PBH 需要將阻止列表的 URL 傳遞給下載器時，它將使用此地址作為前綴。請確保您的下載器可以存取此 URL',
  'page.settings.tab.config.server.prefix.error': '前綴不能以 "/" 結尾',
  'page.settings.tab.config.server.cors': '允許 CORS',
  'page.settings.tab.config.server.cors.tips':
    '允許 CORS 跨站，僅在使用外部 PBH WebUI 時才應該啟用',

  'page.settings.tab.config.logger.title': '日誌',
  'page.settings.tab.config.logger.hide_finish_log': '隱藏完成日誌',
  'page.settings.tab.config.logger.hide_finish_log.tips':
    '是否隱藏 [完成] 已檢查 XX 的 X 個活躍 Torrent 和 X 個對等體 的日誌消息',

  'page.settings.tab.config.lookup.title': '查詢',
  'page.settings.tab.config.lookup.dnsReverseLookup': '啟用 DNS 反向尋找',
  'page.settings.tab.config.lookup.dnsReverseLookup.tips':
    '啟用 DNS 反查，能夠透過 IP 反查域名，但可能增加你所使用 DNS 伺服器的壓力，並可能導致 DNS 伺服器對你採取降低服務品質的措施',

  'page.settings.tab.config.persist.title': '持久化',
  'page.settings.tab.config.persist.banlist': '持久化封禁列表',
  'page.settings.tab.config.persist.ban_logs_keep_days': '封禁日誌保留天數',

  'page.settings.tab.config.btn.enable': '啟用 BTN 模組',
  'page.settings.tab.config.btn.doc': '使用前請閱讀文件：',
  'page.settings.tab.config.btn.enableSubmit': '啟用提交',
  'page.settings.tab.config.btn.enableSubmit.modal.title': '警告',
  'page.settings.tab.config.btn.enableSubmit.modal.content': `BTN 網路基於所有啟用此功能的使用者提交的資料，對 Peers 進行可信度驗證，透過啟用此選項，您也會加入 BTN 網路並提交您的 Torrent 上的活動。以下資訊將被發送到 BTN 實例:`,
  'page.settings.tab.config.btn.enableSubmit.modal.content2':
    '您的 Torrent 列表（包括：Torrent 種子摘要的二次不可逆雜湊和 Torrent 大小），連接到您的 Torrent 的所有 Peers （包括：IP位址、埠號、PeerID、UserAgent（ClientName），Peer協議，Peer總下載量，Peer總上傳量，Peer瞬時上傳速度，Peer瞬時下載速度，Peer下載進度，以及您的下載器名稱）',
  'page.settings.tab.config.btn.enableSubmit.modal.content3': '確定要開啟提交嗎？',
  'page.settings.tab.config.btn.allowScript': '允許 BTN 伺服器下髮腳本',
  'page.settings.tab.config.btn.allowScript.warning':
    '警告：這意味著遠端伺服器可以在你的裝置上執行任意程式碼，請謹慎開啟',
  'page.settings.tab.config.btn.allowScript.tips':
    '打開此選項後將允許 PeerBanHelper 接收並執行來自 BTN 伺服器的動態腳本，這有助於提高反吸血精確度和反吸血效果。',

  'page.settings.tab.config.ipDatabase.title': 'IP 資料庫',
  'page.settings.tab.config.ipDatabase.autoUpdate': '啟用自動更新',
  'page.settings.tab.config.ipDatabase.city': '城市資料庫',
  'page.settings.tab.config.ipDatabase.asn': 'ASN 資料庫',

  'page.settings.tab.config.network': '網路',

  'page.settings.tab.config.reslolver.useSystem': '使用系統 DNS',
  'page.settings.tab.config.reslolver.customServer': '自訂 DNS 伺服器',
  'page.settings.tab.config.proxy': '代理',
  'page.settings.tab.config.proxy.type': '代理類型',
  'page.settings.tab.config.proxy.type.0': '不使用代理',
  'page.settings.tab.config.proxy.type.1': '系統代理',
  'page.settings.tab.config.proxy.type.2': 'HTTP 代理',
  'page.settings.tab.config.proxy.type.3': 'SOCKS5 代理',
  'page.settings.tab.config.proxy.host': '地址',
  'page.settings.tab.config.proxy.port': '埠',
  'page.settings.tab.config.proxy.non_proxy_hosts': '不使用代理的地址',
  'page.settings.tab.config.proxy.non_proxy_hosts.tips':
    '代理例外地址，使用 {separator} 分隔不同條目',

  'page.settings.tab.config.performance.title': '性能',
  'page.settings.tab.config.performance.useEcoQOS': '使用 Windows EcoQos API',
  'page.settings.tab.config.performance.useEcoQOS.tips':
    '啟用 Windows 平台上的 {link}以節約能源消耗，程式執行速度將降低，定時任務可能推遲',

  'page.settings.tab.config.push.title': '消息通知',
  'page.settings.tab.config.push.description':
    '配置消息通知，當有新的事件發生時，將會透過消息通知的方式通知您',
  'page.settings.tab.config.push.add': '新增',
  'page.settings.tab.config.push.edit': '編輯',
  'page.settings.tab.config.push.deleteConfirm': '確定刪除？',
  'page.settings.tab.config.push.form.title.new': '新增推送渠道',
  'page.settings.tab.config.push.form.title.edit': '編輯推送渠道',
  'page.settings.tab.config.push.form.name': '名稱',
  'page.settings.tab.config.push.form.name.placeholder': '請輸入唯一名稱',
  'page.settings.tab.config.push.form.type': '類型',
  'page.settings.tab.config.push.form.type.smtp': '郵件',
  'page.settings.tab.config.push.form.type.pushplus': 'Push+',
  'page.settings.tab.config.push.form.type.serverchan': 'Server醬',
  'page.settings.tab.config.push.form.type.telegram': 'Telegram',

  'page.settings.tab.config.push.form.stmp.host': '主機',
  'page.settings.tab.config.push.form.stmp.port': '埠號',
  'page.settings.tab.config.push.form.stmp.sender': '發件人',
  'page.settings.tab.config.push.form.stmp.senderName': '發件人名稱',
  'page.settings.tab.config.push.form.stmp.auth': '啟用認證',
  'page.settings.tab.config.push.form.stmp.authInfo': '認證資訊',
  'page.settings.tab.config.push.form.stmp.username': '使用者名稱',
  'page.settings.tab.config.push.form.stmp.password': '密碼',
  'page.settings.tab.config.push.form.stmp.encryption': '加密方式',
  'page.settings.tab.config.push.form.stmp.receivers': '收件人',
  'page.settings.tab.config.push.form.stmp.receivers.placeholder': '輸入一個按Enter鍵以輸入下一個',
  'page.settings.tab.config.push.form.stmp.sendPartial': '分片發送',
  'page.settings.tab.config.push.form.stmp.advance': '進階設定',

  'page.settings.tab.config.push.form.pushplus.token': 'Token',
  'page.settings.tab.config.push.form.pushplus.topic': 'Topic',
  'page.settings.tab.config.push.form.pushplus.template': '範本',
  'page.settings.tab.config.push.form.pushplus.channel': 'Channel',

  'page.settings.tab.config.push.form.action.ok': '確定',
  'page.settings.tab.config.push.form.action.cancel': '取消',
  'page.settings.tab.config.push.form.action.test': '測試'
}
