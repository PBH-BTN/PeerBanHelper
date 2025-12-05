export default {
  'page.oobe.action.next': '下一步',
  'page.oobe.action.back': '上一步',

  'page.oobe.steps.welcome': '欢迎',
  'page.oobe.steps.setToken.title': '设置 Token',
  'page.oobe.steps.setToken.description': '信息安全非常重要',
    'page.oobe.steps.btnConfig.title': 'BTN 威胁防护网络',
    'page.oobe.steps.btnConfig.description': '选择 BTN 网络使用模式',
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

    'page.oobe.btnConfig.title': 'BTN 威胁防护网络',
    'page.oobe.btnConfig.description':
        'BTN（BT Network）是一个威胁情报共享网络。您可以选择使用模式，或者完全不使用该功能。',
    'page.oobe.btnConfig.mode.disabled.title': '不使用',
    'page.oobe.btnConfig.mode.disabled.description':
        '使用单机模式，不使用 BTN 网络带来的威胁情报处理功能，也不接收 BTN 云端规则，并不向 BTN 网络提供数据。所有运行数据将在本地设备处理。',
    'page.oobe.btnConfig.mode.anonymous.title': '使用匿名提交（推荐）',
    'page.oobe.btnConfig.mode.anonymous.description':
        '自动使用设备硬件 ID 和 MAC 地址注册匿名账号以接收 BTN 云端规则，并向 BTN 网络提供数据。您提交的数据将参与威胁情报处理，并帮助其它 BTN 用户免于恶意攻击。部分 BTN 服务提供者可能不支持匿名提交。',
    'page.oobe.btnConfig.mode.account.title': '绑定 BTN 账号',
    'page.oobe.btnConfig.mode.account.description':
        '输入在 BTN 站点注册的 App ID 和 App Secret 来绑定这个安装，绑定者的账号将对此安装提交的数据具有控制权限，并可能从站点查看提交的信息和数据。您提交的数据将参与威胁情报处理，并帮助其它 BTN 用户免于恶意攻击。',
    'page.oobe.btnConfig.appId.placeholder': '请输入 App ID',
    'page.oobe.btnConfig.appSecret.placeholder': '请输入 App Secret',

  'page.oobe.addDownloader.title': '添加你的第一个下载器',
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
