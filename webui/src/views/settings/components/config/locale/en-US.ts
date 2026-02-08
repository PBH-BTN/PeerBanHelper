export default {
  'page.settings.tab.config.title': 'Basic Settings',
  'page.settings.tab.config.tips': 'Here are some basic settings that are essential for PBH to run',

  'page.settings.tab.config.unit.day': 'Days',

  'page.settings.tab.config.language': 'Language',
  'page.settings.tab.config.language.default': 'Follow the system',
  'page.settings.tab.config.language.tips':
    'This is the language of the backend program, including return information logs, etc., This is {not} the WebUI language!',
  'page.settings.tab.config.language.tips.not': 'NOT',

  'page.settings.tab.config.plus.button': 'Click to open',

  'page.settings.tab.config.privacy.analytics': 'Enable analytics',
  'page.settings.tab.config.privacy.analytics.tips':
    'The collected anonymous data does not contain private data, is only used to improve software quality, and will not be transmitted to third parties',

  'page.settings.tab.config.server.title': 'WebUI',
  'page.settings.tab.config.server.port': 'Port',
  'page.settings.tab.config.server.port.error': 'The port number must be between 1-65535',
  'page.settings.tab.config.server.address': 'Address',
  'page.settings.tab.config.server.prefix': 'Prefix',
  'page.settings.tab.config.server.prefix.tips':
    'When PBH need pass the URL of blocklist to downloader, it will use this address as prefix, make sure this URL can be access from your downloader.',
  'page.settings.tab.config.server.prefix.error': "Prefix cannot end with '/'",
  'page.settings.tab.config.server.cors': 'Allow CORS',
  'page.settings.tab.config.server.cors.tips':
    'Allow CORS cross-site, this should only be enabled when using an external PBH WebUI',

  'page.settings.tab.config.logger.title': 'Log',
  'page.settings.tab.config.logger.hide_finish_log': 'Hide finish log',
  'page.settings.tab.config.logger.hide_finish_log.tips': 'Hide [Completed] spam logs',

  'page.settings.tab.config.lookup.title': 'Lookup',
  'page.settings.tab.config.lookup.dnsReverseLookup': 'Enable DNS reverse lookup',
  'page.settings.tab.config.lookup.dnsReverseLookup.tips':
    'Enable reverse DNS lookup for IPs, however it will increase your DNS server payload, and DNS server may block your access if too many quires sent to them server',

  'page.settings.tab.config.persist.title': 'Persistence',
  'page.settings.tab.config.persist.banlist': 'Persist banlist',
  'page.settings.tab.config.persist.ban_logs_keep_days': 'Ban logs retention days',

  'page.settings.tab.config.database.type': 'Database Type',
  'page.settings.tab.config.database.type.changeWarning': 'Warning',
  'page.settings.tab.config.database.type.changeWarningContent':
    'Changing the database type will clear existing data. Incorrect database parameters may cause PeerBanHelper to fail to start. Please proceed with caution!',
  'page.settings.tab.config.database.host': 'Host',
  'page.settings.tab.config.database.port': 'Port',
  'page.settings.tab.config.database.database': 'Database Name',
  'page.settings.tab.config.database.username': 'Username',
  'page.settings.tab.config.database.password': 'Password',

  'page.settings.tab.config.banlistRemapping.title': 'Banlist Remapping',
  'page.settings.tab.config.banlistRemapping.description':
    'When updating the banlist to downloaders, map a single banned IP address to a larger range to improve the efficiency of IP banning. Incorrect configuration may ban more unrelated peers.',
  'page.settings.tab.config.banlistRemapping.ipv4.title': 'IPv4 Settings',
  'page.settings.tab.config.banlistRemapping.ipv4.enabled': 'Enable IPv4 remapping',
  'page.settings.tab.config.banlistRemapping.ipv4.enabled.tips':
    'Enable remapping for IPv4 addresses',
  'page.settings.tab.config.banlistRemapping.ipv4.remap_range': 'Prefix length',
  'page.settings.tab.config.banlistRemapping.ipv4.remap_range.tips':
    'The host part of IP address will be set to 0 with this length, and generate corresponding CIDR expression (0-32)',
  'page.settings.tab.config.banlistRemapping.ipv6.title': 'IPv6 Settings',
  'page.settings.tab.config.banlistRemapping.ipv6.enabled': 'Enable IPv6 remapping',
  'page.settings.tab.config.banlistRemapping.ipv6.enabled.tips':
    'Enable remapping for IPv6 addresses',
  'page.settings.tab.config.banlistRemapping.ipv6.remap_range': 'Prefix length',
  'page.settings.tab.config.banlistRemapping.ipv6.remap_range.tips':
    'The host part of IP address will be set to 0 with this length, and generate corresponding CIDR expression (0-128)',

  'page.settings.tab.config.btn.enable': 'Enable BTN',
  'page.settings.tab.config.btn.doc': 'Read the document first:',
  'page.settings.tab.config.btn.enableSubmit': 'Enable submit',
  'page.settings.tab.config.btn.enableSubmit.modal.title': 'Warning',
  'page.settings.tab.config.btn.enableSubmit.modal.content':
    'If this option is enabled (and module also enabled), PBH will generate send data to BTN server including:',
  'page.settings.tab.config.btn.enableSubmit.modal.content2':
    'All peers connected to torrents (Including: IP, Port, PeerID, UserAgent, Peer Protocol, Flags, Uploaded, Downloaded, UploadRate, DownloadRate, PeerProgress, YourProgress and Downloader Name)',
  'page.settings.tab.config.btn.enableSubmit.modal.content3':
    'Are you sure you want to enable submit?',
  'page.settings.tab.config.btn.allowScript': 'Allow BTN server push scripts',
  'page.settings.tab.config.btn.allowScript.warning':
    'Warning: This means that the remote server can execute any code on your device, please enable with caution!',
  'page.settings.tab.config.btn.allowScript.tips':
    'This option will allow BTN server push scripts to your device, this may increase the accuracy of the ban',

  'page.settings.tab.config.ipDatabase.title': 'IP Database',
  'page.settings.tab.config.ipDatabase.autoUpdate': 'Enable auto update',
  'page.settings.tab.config.ipDatabase.city': 'City Database',
  'page.settings.tab.config.ipDatabase.asn': 'ASN Database',

  'page.settings.tab.config.network': 'Network',

  'page.settings.tab.config.reslolver.useSystem': 'Use system DNS',
  'page.settings.tab.config.reslolver.customServer': 'Custom DNS server',
  'page.settings.tab.config.proxy': 'Proxy',
  'page.settings.tab.config.proxy.type': 'Proxy type',
  'page.settings.tab.config.proxy.type.0': 'No proxy',
  'page.settings.tab.config.proxy.type.1': 'HTTP proxy',
  'page.settings.tab.config.proxy.type.2': 'SOCKS5 proxy',
  'page.settings.tab.config.proxy.host': 'Host',
  'page.settings.tab.config.proxy.port': 'Port',
  'page.settings.tab.config.proxy.non_proxy_hosts': 'Non-proxy hosts',
  'page.settings.tab.config.proxy.non_proxy_hosts.tips':
    'Proxy exception address, use {separator} to separate different entries',

  'page.settings.tab.config.performance.title': 'Performance',
  'page.settings.tab.config.performance.useEcoQOS': 'Use Windows EcoQos API',
  'page.settings.tab.config.performance.useEcoQOS.tips':
    'Enable {link} on Windows Platform for power saving, the program performance will reduce and cronjobs may delay',

  'page.settings.tab.config.push.title': 'Message Push',
  'page.settings.tab.config.push.description':
    'By configuring message push, you can receive message push from PBH',
  'page.settings.tab.config.push.add': 'New',
  'page.settings.tab.config.push.edit': 'Edit',
  'page.settings.tab.config.push.deleteConfirm': 'Are you sure to delete?',
  'page.settings.tab.config.push.form.title.new': 'New push channel',
  'page.settings.tab.config.push.form.title.edit': 'Edit push channel',
  'page.settings.tab.config.push.form.name': 'Name',
  'page.settings.tab.config.push.form.name.placeholder': 'Enter a unique name',
  'page.settings.tab.config.push.form.type': 'Type',
  'page.settings.tab.config.push.form.type.smtp': 'E-Mail',
  'page.settings.tab.config.push.form.type.pushplus': 'Push+',
  'page.settings.tab.config.push.form.type.serverchan': 'ServerChan',
  'page.settings.tab.config.push.form.type.telegram': 'Telegram',
  'page.settings.tab.config.push.form.type.bark': 'Bark',
  'page.settings.tab.config.push.form.type.pushdeer': 'PushDeer',
  'page.settings.tab.config.push.form.type.gotify': 'Gotify',

  'page.settings.tab.config.push.form.stmp.host': 'Host',
  'page.settings.tab.config.push.form.stmp.port': 'Port',
  'page.settings.tab.config.push.form.stmp.sender': 'Sender',
  'page.settings.tab.config.push.form.stmp.senderName': 'Sender Name',
  'page.settings.tab.config.push.form.stmp.auth': 'Enable Auth',
  'page.settings.tab.config.push.form.stmp.authInfo': 'Auth Info',
  'page.settings.tab.config.push.form.stmp.username': 'Username',
  'page.settings.tab.config.push.form.stmp.password': 'Password',
  'page.settings.tab.config.push.form.stmp.encryption': 'Encryption',
  'page.settings.tab.config.push.form.stmp.receivers': 'Receivers',
  'page.settings.tab.config.push.form.stmp.receivers.placeholder':
    "Enter one receivers, then press 'Enter'",
  'page.settings.tab.config.push.form.stmp.sendPartial': 'Send Partial',
  'page.settings.tab.config.push.form.stmp.advance': 'Advance Config',

  'page.settings.tab.config.push.form.pushplus.token': 'Token',
  'page.settings.tab.config.push.form.pushplus.topic': 'Topic',
  'page.settings.tab.config.push.form.pushplus.template': 'Template',
  'page.settings.tab.config.push.form.pushplus.channel': 'Channel',

  'page.settings.tab.config.push.form.pushdeer.endpoint': 'Endpoint',
  'page.settings.tab.config.push.form.pushdeer.pushkey': 'Push Key',

  'page.settings.tab.config.push.form.gotify.endpoint': 'Endpoint',
  'page.settings.tab.config.push.form.gotify.priority': 'Priority',

  'page.settings.tab.config.push.form.action.ok': 'Ok',
  'page.settings.tab.config.push.form.action.cancel': 'Cancel',
  'page.settings.tab.config.push.form.action.test': 'Test'
}
