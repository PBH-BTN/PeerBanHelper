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

  'page.settings.tab.config.privacy.errorReport': 'Enable error report',

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

  'page.settings.tab.config.btn.enable': 'Enable BTN',
  'page.settings.tab.config.btn.doc': 'Read the document first(Chinese only):',
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
    'Warning, this means that the remote server can execute any code on your device, please enable with caution!',
  'page.settings.tab.config.btn.allowScript.tips':
    'This option will allow BTN server push scripts to your device, this may increase the accuracy of the ban',

  'page.settings.tab.config.ipDatabase.title': 'IP Database',
  'page.settings.tab.config.ipDatabase.autoUpdate': 'Enable auto update',
  'page.settings.tab.config.ipDatabase.city': 'City Database',
  'page.settings.tab.config.ipDatabase.asn': 'ASN Database',

  'page.settings.tab.config.proxy': 'Proxy',
  'page.settings.tab.config.proxy.type': 'Proxy type',
  'page.settings.tab.config.proxy.type.0': 'No proxy',
  'page.settings.tab.config.proxy.type.1': 'System proxy',
  'page.settings.tab.config.proxy.type.2': 'HTTP proxy',
  'page.settings.tab.config.proxy.type.3': 'SOCKS5 proxy',
  'page.settings.tab.config.proxy.host': 'Host',
  'page.settings.tab.config.proxy.port': 'Port',
  'page.settings.tab.config.proxy.non_proxy_hosts': 'Non-proxy hosts',
  'page.settings.tab.config.proxy.non_proxy_hosts.tips':
    'Proxy exception address, use {separator} to separate different entries',

  'page.settings.tab.config.performance.title': 'Performance',
  'page.settings.tab.config.performance.useEcoQOS': 'Use Windows EcoQos API',
  'page.settings.tab.config.performance.useEcoQOS.tips':
    'Enable {link} on Windows Platform for power saving, the program performance will reduce and cronjobs may delay'
}
