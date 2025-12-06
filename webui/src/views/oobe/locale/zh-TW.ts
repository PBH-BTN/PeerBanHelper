export default {
  'page.oobe.action.next': '下一步',
  'page.oobe.action.back': '上一步',

  'page.oobe.steps.welcome': '歡迎',
  'page.oobe.steps.setToken.title': '設定 Token',
  'page.oobe.steps.setToken.description': '資訊安全非常重要',
  'page.oobe.steps.btnConfig.title': 'BTN 威脅防護網路',
  'page.oobe.steps.btnConfig.description': '選擇 BTN 網路使用模式',
  'page.oobe.steps.addDownloader.title': '添加下載器',
  'page.oobe.steps.success.title': '初始化完成',
  'page.oobe.steps.success.description': '開始使用！',

  'page.oobe.welcome.title': '歡迎使用 PeerBanHelper!',
  'page.oobe.welcome.description':
    'PeerBanHelper 是一個 BT 反吸血工具。透過連接到您的下載器的 WebUI，PeerBanHelper 可以使用這些資訊檢查並封禁惡意吸血者。在一切開始之前，讓我們先進行一些簡單配置。',
  'page.oobe.steps.welcome.docTips':
    '請在開始前閱讀{privacy}並打開{doc}放在旁邊以作備用，99% 的使用者因為不閱讀它們導致遇到了問題無法解決。',
  'page.oobe.steps.welcome.doc': '安裝和故障排除指南——也就是使用者說明書',
  'page.oobe.steps.welcome.privacy': '隱私協議',
  'page.oobe.steps.welcome.privacy.accept': '我已經充分閱讀並同意上述{privacy}',
  'page.oobe.setToken.title': '設定 Token',
  'page.oobe.setToken.description':
    '首先你需要設定一個 Token，這個 Token 可以保護你的 WebUI 不被惡意存取，並保護您的隱私。務必確保強度夠高。一旦您成功登入過一次 WebUI，後續瀏覽器就會記住 Token 並自動登入。',
  'page.oobe.setToken.generate': '隨機生成',

  'page.oobe.btnConfig.title': 'BTN 威脅防護網路',
  'page.oobe.btnConfig.description':
    'BTN (BitTorrent Threat Network) 是一個 "人人為我，我為人人" 的 BitTorrent 威脅情報網路。通過連接到該網路，您將獲取由其它 BTN 使用者提交貢獻的資料分析得到的雲端規則。通過這種方式，BTN 能夠以相當快的速度發現干擾 BitTorrent 網路運行的 Peer，並提前通知 PeerBanHelper 處置這些 IP 位址。\n秉承自願原則，您可以自行選擇 BTN 網路的運行方式。相關更改在安裝嚮導結束後，可以在設定中隨時修改。',
  'page.oobe.btnConfig.mode.disabled.title': '不使用',
  'page.oobe.btnConfig.mode.disabled.description':
    '使用單機模式，不使用 BTN 網路帶來的威脅情報處理功能，也不接收 BTN 雲端規則，並不向 BTN 網路提供資料。所有執行資料將在本機裝置處理。',
  'page.oobe.btnConfig.mode.anonymous.title': '使用自動註冊配置（推薦）',
  'page.oobe.btnConfig.mode.anonymous.description':
    '使用裝置硬體 ID 生成的雜湊值和安裝 ID 自動註冊匿名帳號以接收 BTN 雲端規則，並向 BTN 網路提供資料，該方式不會將您的社交帳號與 BTN 系統綁定。您提交的資料將參與威脅情報處理，並幫助其它 BTN 使用者免於惡意攻擊。部分 BTN 服務提供者可能不支援匿名提交。如果您不希望提交資料，可稍後在設定中關閉。',
  'page.oobe.btnConfig.mode.account.title': '綁定 BTN 帳號',
  'page.oobe.btnConfig.mode.account.description':
    '輸入在 BTN 站點註冊的 App ID 和 App Secret 來綁定這個安裝，綁定者的帳號將對此安裝提交的資料具有控制權限，並可能從站點查看提交的資訊和資料。您提交的資料將參與威脅情報處理，並幫助其它 BTN 使用者免於惡意攻擊。',
  'page.oobe.btnConfig.appId.placeholder': '請輸入 App ID',
  'page.oobe.btnConfig.appSecret.placeholder': '請輸入 App Secret',

  'page.oobe.addDownloader.title': '添加你的第一個下載器',
  'page.oobe.addDownloader.description':
    '首先選擇你的下載器類型，隨後填寫下載器的 WebUI 的相關認證資訊。',
  'page.oobe.addDownloader.test': '測試下載器',
  'page.oobe.addDownloader.test.success': '測試成功',

  'page.oobe.result.title': '初始化完成！',
  'page.oobe.result.title.error': '初始化失敗',
  'page.oobe.result.description': '你已經完成了初始化，現在可以開始使用 PeerBanHelper 了！',
  'page.oobe.result.goto': '開始使用',
  'page.oobe.result.initlizing': '正在初始化，請稍後...',
  'page.oobe.result.retry': '重試'
}
