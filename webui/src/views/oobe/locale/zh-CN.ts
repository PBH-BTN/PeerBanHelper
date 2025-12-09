export default {
  'page.oobe.action.next': '下一步',
  'page.oobe.action.back': '上一步',

  'page.oobe.steps.welcome': '欢迎',
  'page.oobe.steps.setToken.title': '设置 Token',
  'page.oobe.steps.setToken.description': '信息安全非常重要',
  'page.oobe.steps.addDownloader.title': '添加下载器',
  'page.oobe.steps.success.title': '初始化完成',
  'page.oobe.steps.success.description': '开始使用！',

  'page.oobe.welcome.title': '欢迎使用 PeerBanHelper!',
  'page.oobe.welcome.description':
    'PeerBanHelper 是一个 BT 反吸血工具。通过连接到您的下载器的 WebUI，PeerBanHelper 可以使用这些信息检查并封禁恶意吸血者。在一切开始之前，让我们先进行一些简单配置。',
  'page.oobe.steps.welcome.docTips':
    '请在开始前阅读{privacy}并打开{doc}放在旁边以作备用，99% 的用户因为不阅读它们导致遇到了问题无法解决。',
  'page.oobe.steps.welcome.doc': '安装和故障排除指南——也就是用户说明书',
  'page.oobe.steps.welcome.privacy': '隐私协议',
  'page.oobe.steps.welcome.privacy.accept': '我已经充分阅读并同意上述{privacy}',

  'page.oobe.setToken.title': '设置 Token',
  'page.oobe.setToken.description':
    '首先你需要设置一个 Token，这个 Token 可以保护你的 WebUI 不被恶意访问，并保护您的隐私。务必确保强度够高。一旦您成功登陆过一次 WebUI，后续浏览器就会记住 Token 并自动登录。',
  'page.oobe.setToken.generate': '随机生成',

  'page.oobe.btnConfig.title': 'BTN',
  'page.oobe.btnConfig.briefDescription': '威胁防护网络',
  'page.oobe.btnConfig.description':
    'BTN (BitTorrent Threat Network) 是一个 "人人为我，我为人人" 的 BitTorrent 威胁情报网络。通过连接到该网络，您将获取由其它 BTN 用户提交贡献的数据分析得到的云端规则。通过这种方式，BTN 能够以相当快的速度发现干扰 BitTorrent 网络运行的 Peer，并提前通知 PeerBanHelper 处置这些 IP 地址。\n秉承自愿原则，您可以自行选择 BTN 网络的运行方式。相关更改在安装向导结束后，可以在设置中随时修改。',
  'page.oobe.btnConfig.mode.disabled.title': '不使用',
  'page.oobe.btnConfig.mode.disabled.description':
    '使用单机模式，不使用 BTN 网络带来的威胁情报处理功能，也不接收 BTN 云端规则，并不向 BTN 网络提供数据。所有运行数据将在本地设备处理。',
  'page.oobe.btnConfig.mode.anonymous.title': '使用自动注册配置（推荐）',
  'page.oobe.btnConfig.mode.anonymous.description':
    '使用设备硬件 ID 生成的哈希值和安装 ID 自动注册匿名账号以接收 BTN 云端规则，并向 BTN 网络提供数据，该方式不会将您的社交帐号与 BTN 系统绑定。您提交的数据将参与威胁情报处理，并帮助其它 BTN 用户免于恶意攻击。部分 BTN 服务提供者可能不支持匿名提交。如果您不希望提交数据，可稍后在设置中关闭。',
  'page.oobe.btnConfig.mode.account.title': '已有 BTN 账号',
  'page.oobe.btnConfig.mode.account.description':
    '输入在 BTN 站点注册的 App ID 和 App Secret 来绑定这个安装，绑定者的账号将对此安装提交的数据具有控制权限，并可能从站点查看提交的信息和数据。您提交的数据将参与威胁情报处理，并帮助其它 BTN 用户免于恶意攻击。',
  'page.oobe.btnConfig.appId.placeholder': '请输入 App ID',
  'page.oobe.btnConfig.appSecret.placeholder': '请输入 App Secret',

  'page.oobe.addDownloader.title': '添加你的第一个下载器',
  'page.oobe.addDownloader.scan': '扫描下载器',
  'page.oobe.addDownloader.scan.tooltip': '这里是用户知情同意占位符',
  'page.oobe.addDownloader.scan.noDownloader': '未发现可用的下载器',
  'page.oobe.addDownloader.scan.type': '类型',
  'page.oobe.addDownloader.scan.multi': '找到多个下载器，请手动选择',
  'page.oobe.addDownloader.scan.one': '找到一个下载器，已自动填写相关信息',
  'page.oobe.addDownloader.scan.cancel': '取消',
  'page.oobe.addDownloader.scan.select': '选择',
  'page.oobe.addDownloader.description':
    '首先选择你的下载器类型，随后填写下载器的 WebUI 的相关认证信息。',
  'page.oobe.addDownloader.test': '测试下载器',
  'page.oobe.addDownloader.test.success': '测试成功',

  'page.oobe.result.title': '初始化完成！',
  'page.oobe.result.title.error': '初始化失败',
  'page.oobe.result.description': '你已经完成了初始化，现在可以开始使用 PeerBanHelper 了！',
  'page.oobe.result.goto': '开始使用',
  'page.oobe.result.initlizing': '正在初始化，请稍后...',
  'page.oobe.result.retry': '重试'
}
