export default {
  'page.settings.tab.profile.save': 'Save',
  'page.settings.tab.profile.base.title': 'Global Config',
  'page.settings.tab.profile.form.checkInterval': 'Check Interval',
  'page.settings.tab.profile.unit.ms': 'ms',
  'page.settings.tab.profile.unit.s': 'seconds',
  'page.settings.tab.profile.unit.bytes': 'bytes',
  'page.settings.tab.profile.form.banDuration': 'Use Global Ban Duration',
  'page.settings.tab.profile.form.ignoreAddress': 'Ignore Address',
  'page.settings.tab.profile.form.ignoreAddress.action': 'Click to view',
  'page.settings.tab.profile.form.ignoreAddress.tooltip':
    'Bypass list, all peers comes from those IPs will bypass all checks',
  'page.settings.tab.profile.module.title': 'Module Config',
  'page.settings.tab.profile.module.enable': 'Enable',

  'page.settings.tab.profile.formArray.emptyTips':
    'Please fill in all fields with non-whitespace content',

  'page.settings.tab.profile.module.peerIdBlackList.if': 'If',
  'page.settings.tab.profile.module.peerIdBlackList.hit': 'Hit',
  'page.settings.tab.profile.module.peerIdBlackList.miss': 'Miss',
  'page.settings.tab.profile.module.banRuleTips.STARTS_WITH': 'Starts with',
  'page.settings.tab.profile.module.banRuleTips.ENDS_WITH': 'Ends with',
  'page.settings.tab.profile.module.banRuleTips.LENGTH': 'Match the string length',
  'page.settings.tab.profile.module.banRuleTips.CONTAINS': 'Contains',
  'page.settings.tab.profile.module.banRuleTips.EQUALS': 'Equals',
  'page.settings.tab.profile.module.banRuleTips.REGEX': 'Regex',

  'page.settings.tab.profile.module.banRuleTips.DEFAULT': 'Default',
  'page.settings.tab.profile.module.banRuleTips.TRUE': 'Ban',
  'page.settings.tab.profile.module.banRuleTips.FALSE': 'Exclude',

  'page.settings.tab.profile.module.banRuleTips.empty': 'Please fill the empty fields',

  'page.settings.tab.profile.module.peerIdBlackList': 'PeerID blacklist',
  'page.settings.tab.profile.module.peerIdBlackList.useGlobalBanTime': 'Use global ban duration',
  'page.settings.tab.profile.module.peerIdBlackList.banPeerId': 'Banned PeerID',
  'page.settings.tab.profile.module.peerIdBlackList.rule': 'Rule',

  'page.settings.tab.profile.module.clientNameBlackList': 'ClientName blacklist',
  'page.settings.tab.profile.module.clientNameBlackList.useGlobalBanTime':
    'Use global ban duration',
  'page.settings.tab.profile.module.clientNameBlackList.banClientName': 'Banned ClientName',
  'page.settings.tab.profile.module.clientNameBlackList.placeholder': 'Client Name',

  'page.settings.tab.profile.module.progressCheatBlocker': 'Cheating Progress blocker',
  'page.settings.tab.profile.module.progressCheatBlocker.tips':
    'Note: Sometimes it may incorrectly ban some clients who enabled "Super Seeding", but in most cases, it can accurately detect the cheating/bad peers.',
  'page.settings.tab.profile.module.progressCheatBlocker.minSize': 'Minimum file size',
  'page.settings.tab.profile.module.progressCheatBlocker.minSize.tips':
    'Skip the check if torrent smaller than this value, unit: bytes, peer may have no chance to sync the progress',
  'page.settings.tab.profile.module.progressCheatBlocker.maxDifference':
    'Maximum progress difference',
  'page.settings.tab.profile.module.progressCheatBlocker.maxDifference.tips':
    "PeerBanHelper will use BT client recorded data to check the actual uploaded bytes, and calculate minimal progress that this peer should have and compare with peer reported progress. If peer reported progress is smaller than our calculated progress too much, we will consider it's cheating. It works well on detecting new various and cheat clients.",
  'page.settings.tab.profile.module.progressCheatBlocker.progressRewindDetection':
    'Progress rewind detection',
  'page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference':
    'Maximum rewind progress difference',
  'page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference.tips':
    'Sometimes the pieces may break during transfer, client may drop those pieces, we allow client have rewind in reasonable range',
  'page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients':
    'Block excessive clients',
  'page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients.tips':
    'Block clients that download more than the torrent size',
  'page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold':
    'Excessive download threshold',
  'page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold.tips':
    'IsExcessive = uploaded > (torrent_size * excessive_threshold)',
  'page.settings.tab.profile.module.progressCheatBlocker.ipv4prefixlength': 'IPv4 prefix length',
  'page.settings.tab.profile.module.progressCheatBlocker.ipv6prefixlength': 'IPv6 prefix length',
  'page.settings.tab.profile.module.progressCheatBlocker.ipprefixLength.tips':
    'IPs from the same subnet are considered as the same user',
  'page.settings.tab.profile.module.progressCheatBlocker.useGlobalBanTime':
    'Use global ban duration',
  'page.settings.tab.profile.module.progressCheatBlocker.enablePersist': 'Enable persist recording',
  'page.settings.tab.profile.module.progressCheatBlocker.enablePersist.tips':
    'Enable this feature may increase disk I/O and may affect performance, even may cause flash wear on embedded devices',
  'page.settings.tab.profile.module.progressCheatBlocker.persistDuration': 'Persist duration',
  'page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration':
    'Max wait duration before ban',
  'page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration.tips':
    "Sometimes due the network issue, the peer may cannot sync the progress information on time, When a Peer reached ban condition, the timer will start and Peer will be banned after timer timed out if Peer's progress not update to excepted value on time ",
  'page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest': 'Enable fast PCB test',
  'page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest.tips':
    'This option will allow PCB ban the Peer from downloader for disconnect it, this will heat up progress reset check quickly.',
  'page.settings.tab.profile.module.progressCheatBlocker.fastPCBTestPercentage':
    'Fast PCB test threshold',

  'page.settings.tab.profile.module.ipAddressBlocker.title': 'IP address/port blacklist',
  'page.settings.tab.profile.module.ipAddressBlocker.useGlobalBanTime': 'Use global ban duration',
  'page.settings.tab.profile.module.ipAddressBlocker.rules':
    'Configure rules please go to the {link} page',
  'page.settings.tab.profile.module.ipAddressBlocker.rules.link': 'Rule Configuration',

  'page.settings.tab.profile.module.autoRangeBan.title': 'Range ban',
  'page.settings.tab.profile.module.autoRangeBan.tips':
    'After a peer got banned, other connected peers that in same range with banned peers will also get banned.',
  'page.settings.tab.profile.module.autoRangeBan.useGlobalBanTime': 'Use global ban duration',
  'page.settings.tab.profile.module.autoRangeBan.ipv4Prefix': 'IPv4 prefix length',
  'page.settings.tab.profile.module.autoRangeBan.ipv6Prefix': 'IPv6 prefix length',

  'page.settings.tab.profile.module.btn.enable.tips':
    'Enable the network rules from BTN server, only works when you configured BTN server in config.yml',

  'page.settings.tab.profile.module.multiDialingBlocker.title': 'Multi-dialing blocker',
  'page.settings.tab.profile.module.multiDialingBlocker.useGlobalBanTime':
    'Use global ban duration',
  'page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-length': 'Subnet mask length',
  'page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-v6-length':
    'IPv6 subnet mask length',
  'page.settings.tab.profile.module.multiDialingBlocker.tolerate-num': 'Tolerate number({version})',
  'page.settings.tab.profile.module.multiDialingBlocker.tolerate-num.tips':
    'The allowed maximum amount of ips in same subnet, to avoid mistake bans that caused by DHCP re-allocated IPs, or multiple users in same ISP',
  'page.settings.tab.profile.module.multiDialingBlocker.timeWindow': 'Detection window',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting': 'Keep hunting',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting.tips':
    'If a specific IP flagged multi-dialing, ignore the caching span and keep searching other IPs in same subnet',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting-time': 'Keep hunting time',

  'page.settings.tab.profile.module.expressionEngine.title': 'AviatorScript rule engine',
  'page.settings.tab.profile.module.expressionEngine.tips':
    'Enable this feature can use AviatorScript language to write complex rules to achieve more custom functions',
  'page.settings.tab.profile.module.expressionEngine.useGlobalBanTime': 'Use global ban duration',

  'page.settings.tab.profile.module.ruleSubscribe.title': 'Rule subscribe',
  'page.settings.tab.profile.module.ruleSubscribe.enable.tips':
    'Enable the network rules from BTN server, only works when you configured BTN server in config.yml',
  'page.settings.tab.profile.module.ruleSubscribe.useGlobalBanTime': 'Use global ban duration',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe':
    'For other configurations, please go to the {link} page',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe.link': 'Rule Subscribe',

  'page.settings.tab.profile.module.activeMonitor.title': 'Active monitoring',
  'page.settings.tab.profile.module.activeMonitor.enable.tips':
    'This feature allows PeerBanHelper to monitor the network traffic activity of downloader, automatically adjust its transfer rate settings, and send alert messages when the threshold is reached',
  'page.settings.tab.profile.module.activeMonitor.disable.tips':
    'This function is required by some charts.',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.enable':
    'Enable daily traffic alert',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.tips':
    'Set the traffic alert threshold, when the threshold is exceeded, an alert message will be sent to remind you to check the status of the downloader.',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.value':
    'Daily traffic alert threshold',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.enable':
    'Enable Traffic Capping by Sliding Window',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.enable.tips':
    'If enabled, this module will take over all upload rate control settings in downloader',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.daily_max_allowed_upload_traffic':
    'Max allowed upload traffic in sliding window',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.daily_max_allowed_upload_traffic.tips':
    'The maximum allowed upload traffic in sliding window',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed':
    'Minimum speed',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed.tips':
    'The minimum allowed download rate when adjusting upload rate',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed.warning':
    'If minimum speed is set, the above daily maximum allowed upload traffic may not be obeyed',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.max_speed':
    'Maximum speed',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.max_speed.tips':
    'The maximum allowed download rate when adjusting upload rate',
  'page.settings.tab.profile.module.idleConnectionDosProtection.title': 'DoS Protection Module',
  'page.settings.tab.profile.module.idleConnectionDosProtection.enable.tips':
    'This module protects the downloader that associated with PBH from DoS attack',
  'page.settings.tab.profile.module.idleConnectionDosProtection.useGlobalBanTime':
    'Use global ban duration',
  'page.settings.tab.profile.module.idleConnectionDosProtection.maxAllowedIdleTime':
    'Max allowed idle time',
  'page.settings.tab.profile.module.idleConnectionDosProtection.maxAllowedIdleTime.tips':
    'When a Peer has no data transfer or status updates for a long time and speed is consistently below the idle speed threshold, it will be considered as an idle connection',
  'page.settings.tab.profile.module.idleConnectionDosProtection.idleSpeedThreshold':
    'Idle speed threshold',
  'page.settings.tab.profile.module.idleConnectionDosProtection.idleSpeedThreshold.tips':
    'Connection with speed lower than this value will be considered as idle connection',
  'page.settings.tab.profile.module.idleConnectionDosProtection.minStatusChangePercentage':
    'Minimum status change percentage',
  'page.settings.tab.profile.module.idleConnectionDosProtection.minStatusChangePercentage.tips':
    'When status updates, changes below this percentage will still be considered idle',
  'page.settings.tab.profile.module.idleConnectionDosProtection.resetOnStatusChange':
    'Reset timer on status change',
  'page.settings.tab.profile.module.idleConnectionDosProtection.resetOnStatusChange.tips':
    'Consider connection active when status update (e.g progress), reset the timer',

  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.title':
    'Session Analyse Service',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.enable.tips':
    'Record the peer status data in the analyse interval, generate statistical report',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataFlushInterval':
    'Data flush interval',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataFlushInterval.tips':
    'Time interval to flush peer status data to database during the analyse period',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.cleanupInterval':
    'Cleanup interval',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.cleanupInterval.tips':
    'Time interval to aggregate statistics and cleanup expired data',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataRetentionTime':
    'Data retention time',
  'page.settings.tab.profile.module.peerAnalyseService.sessionAnalyse.dataRetentionTime.tips':
    'Historical session data older than this time will be automatically deleted',
  'page.settings.tab.profile.module.peerAnalyseService.swarmTracking.title':
    'Peer Swarm Recording Service',
  'page.settings.tab.profile.module.peerAnalyseService.swarmTracking.tips':
    'Provide data support for BTN swarm tracking and other functions that require this data, and track peer data during this running session, data will be automatically deleted after restart',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.title':
    'Peer Recording Service',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.enable.tips':
    'Continuously track and record the status, session, transfer, offset and other data of Peers, provide data support for IP and Torrent aggregated data analysis and chart generation, BTN cross-user Peer tracking capabilities',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataFlushInterval':
    'Data flush interval',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataFlushInterval.tips':
    'Time interval to write peer status, session, transfer, offset and other data to database',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataRetentionTime':
    'Data retention time',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataRetentionTime.tips':
    'Retention time for continuously tracked peer data, data older than this time will be deleted',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataCleanupInterval':
    'Data cleanup interval',
  'page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataCleanupInterval.tips':
    'Time interval to execute data cleanup tasks, do not set it too frequently to avoid performance impact',

  'page.settings.tab.profile.module.antiVampire.title': 'Anti-Vampire',
  'page.settings.tab.profile.module.antiVampire.enable.tips':
    'By monitoring the transfer situation between you and the Peer, make behavior judgments, and ban the leeching clients',
  'page.settings.tab.profile.module.antiVampire.useGlobalBanTime': 'Use global ban time',
  'page.settings.tab.profile.module.antiVampire.presets.title': 'Detection Presets',
  'page.settings.tab.profile.module.antiVampire.presets.xunlei.title': 'Xunlei',
  'page.settings.tab.profile.module.antiVampire.presets.xunlei.enabled': 'Enable',
  'page.settings.tab.profile.module.antiVampire.presets.xunlei.enabled.tips':
    'Strategy: A) In seeding state, block all versions of Xunlei clients as no version of Xunlei will seed; B) In downloading state, only allow Xunlei 0.0.1.9 clients as they normally participate in Swarm data sharing during task download'
}
