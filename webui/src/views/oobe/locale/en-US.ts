export default {
  'page.oobe.action.next': 'Next',
  'page.oobe.action.back': 'Back',

  'page.oobe.steps.welcome': 'Welcome',
  'page.oobe.steps.setToken.title': 'Set Token',
  'page.oobe.steps.setToken.description': 'Security comes first',
  'page.oobe.steps.btnConfig.title': 'BitTorrent Threat Network',
  'page.oobe.steps.btnConfig.description': 'Choose BTN Network mode',
  'page.oobe.steps.addDownloader.title': 'Add Downloader',
  'page.oobe.steps.success.title': 'Initialization success',
  'page.oobe.steps.success.description': 'Start using!',

  'page.oobe.welcome.title': 'Welcome to PeerBanHelper!',
  'page.oobe.welcome.description':
    "PeerBanHelper is a tool for anti-leech. PBH will connect to your downloader and check if any bad peers connected to your downloader. Once any bad peer was found, PBH will ban it. Before we start, let's do some simple config first.",
  'page.oobe.steps.welcome.docTips':
    'Before getting started, please read our {privacy} and keep the {doc} open for reference. Statistics show that 99% of issues arise because users do not read these documents.',
  'page.oobe.steps.welcome.doc': 'Setup & Troubleshooting Guide -- also known as the user manual',
  'page.oobe.steps.welcome.privacy': 'Privacy Policy',
  'page.oobe.steps.welcome.privacy.accept': 'I have read and agree to the above {privacy}',
  'page.oobe.setToken.title': 'Set Token',
  'page.oobe.setToken.description':
    'First, you need to set a token, this token can prevent your WebUI from malicious access and protect your privacy. Once you login to your WebUI once, your browser will remember it and automaticlly login in future.',
  'page.oobe.setToken.generate': 'Generate',

  'page.oobe.btnConfig.title': 'BitTorrent Threat Network',
  'page.oobe.btnConfig.description':
      'BTN (BitTorrent Threat Network) is a threat intelligence sharing network. You can choose the usage mode or disable it completely.',
  'page.oobe.btnConfig.mode.disabled.title': 'Disabled',
  'page.oobe.btnConfig.mode.disabled.description':
      'Use standalone mode without BTN network threat intelligence processing, cloud rules, or data submission. All data will be processed locally on your device.',
  'page.oobe.btnConfig.mode.anonymous.title': 'Use Anonymous Submission (Recommended)',
  'page.oobe.btnConfig.mode.anonymous.description':
      'Automatically register an anonymous account using device hardware ID and MAC address to receive BTN cloud rules and submit data to the BTN network. Your submitted data will participate in threat intelligence processing and help other BTN users defend against malicious attacks. Some BTN service providers may not support anonymous submission.',
  'page.oobe.btnConfig.mode.account.title': 'Bind BTN Account',
  'page.oobe.btnConfig.mode.account.description':
      "Enter the App ID and App Secret registered on the BTN site to bind this installation. The binder's account will have control permissions over the data submitted by this installation and may view submitted information and data from the site. Your submitted data will participate in threat intelligence processing and help other BTN users defend against malicious attacks.",
  'page.oobe.btnConfig.appId.placeholder': 'Enter App ID',
  'page.oobe.btnConfig.appSecret.placeholder': 'Enter App Secret',

  'page.oobe.addDownloader.title': 'Add your first downloader',
  'page.oobe.addDownloader.description':
    "Choose your downloader type first, then fill in the downloader's WebUI related authentication information.",
  'page.oobe.addDownloader.test': 'Test downloader',
  'page.oobe.addDownloader.test.success': 'Test success',

  'page.oobe.result.title': 'Initialization success!',
  'page.oobe.result.title.error': 'Initialization failed',
  'page.oobe.result.description':
    'You have completed the initialization, now you can start using PeerBanHelper!',
  'page.oobe.result.goto': 'Start using',
  'page.oobe.result.initlizing': 'Initializing, This may take a while...',
  'page.oobe.result.retry': 'Retry'
}
