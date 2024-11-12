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
  'page.settings.tab.profile.module.clientNameBlackList.rule': 'Rule',

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
  'page.settings.tab.profile.module.progressCheatBlocker.banDuration': 'Ban Duration',
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
  'page.settings.tab.profile.module.ruleSubscribe.useGlobalBanTime': 'Use global ban duration',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe':
    'For other configurations, please go to the {link} page',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe.link': 'Rule Subscribe',

  'page.settings.tab.profile.module.activeMonitor.title': 'Active monitoring',
  'page.settings.tab.profile.module.activeMonitor.disable.tips':
    'This function is required by some charts.',
  'page.settings.tab.profile.module.activeMonitor.dataRetentionTime': 'Retention time',
  'page.settings.tab.profile.module.activeMonitor.dataRetentionTime.tips':
    "Deleted records won't free the disk space, but new data will reuse those parts of space due SQLite internal design",
  'page.settings.tab.profile.module.activeMonitor.dataCleanupInterval': 'Cleanup interval',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.enable':
    'Enable daily traffic alert',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.tips':
    'Set the traffic alert threshold, when the threshold is exceeded, an alert message will be sent to remind you to check the status of the downloader.',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.value':
    'Daily traffic alert threshold'
}
