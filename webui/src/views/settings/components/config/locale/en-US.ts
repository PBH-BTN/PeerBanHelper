export default {
  'page.settings.tab.config.save': 'Save',
  'page.settings.tab.config.base.title': 'Base Config',
  'page.settings.tab.config.form.checkInterval': 'Check Interval',
  'page.settings.tab.config.unit.ms': 'ms',
  'page.settings.tab.config.unit.s': 'seconds',
  'page.settings.tab.config.form.banDuration': 'Ban Duration',
  'page.settings.tab.config.form.ingoreAddress': 'Ignore Address',
  'page.settings.tab.config.form.ingoreAddress.tooltip':
    'Bypass list, all peers comes from those IPs will bypass all checks',
  'page.settings.tab.config.module.title': 'Module Config',
  'page.settings.tab.config.module.enable': 'Enable',

  'page.settings.tab.config.module.peerIdBlackList.if': 'If',
  'page.settings.tab.config.module.peerIdBlackList.hit': 'Hit',
  'page.settings.tab.config.module.peerIdBlackList.miss': 'Miss',
  'page.settings.tab.config.module.banRuleTips.STARTS_WITH': 'Starts with',
  'page.settings.tab.config.module.banRuleTips.ENDS_WITH': 'Ends with',
  'page.settings.tab.config.module.banRuleTips.LENGTH': 'Match the string length',
  'page.settings.tab.config.module.banRuleTips.CONTAINS': 'Contains',
  'page.settings.tab.config.module.banRuleTips.EQUALS': 'Equals',
  'page.settings.tab.config.module.banRuleTips.REGEX': 'Regex',

  'page.settings.tab.config.module.banRuleTips.DEFAULT': 'Default',
  'page.settings.tab.config.module.banRuleTips.TRUE': 'Ban',
  'page.settings.tab.config.module.banRuleTips.FALSE': 'Exclude',

  'page.settings.tab.config.module.peerIdBlackList': 'PeerId blacklist',
  'page.settings.tab.config.module.peerIdBlackList.individualBanTime': 'Use global ban duration',
  'page.settings.tab.config.module.peerIdBlackList.banPeerId': 'Banned PeerId',
  'page.settings.tab.config.module.peerIdBlackList.rule': 'Rule',

  'page.settings.tab.config.module.clientNameBlackList': 'ClientName blacklist',

  'page.settings.tab.config.module.progressCheatBlocker': 'Cheatting Progress blocker',
  'page.settings.tab.config.module.progressCheatBlocker.tips':
    'Note: Sometimes it may incorrectly ban some clients who enabled "Super Seeding", but in most cases, it can accurately detect the cheatting/bad peers.',
  'page.settings.tab.config.module.progressCheatBlocker.minSize': 'Minimum file size',
  'page.settings.tab.config.module.progressCheatBlocker.minSize.tips':
    'Skip the check if torrent smaller than this value, unit: bytes, peer may have to no chance to sync the progress',
  'page.settings.tab.config.module.progressCheatBlocker.maxDifference':
    'Maximum progress difference',
  'page.settings.tab.config.module.progressCheatBlocker.maxDifference.tips':
    "PeerBanHelper will use BT client recorded data to check the actual uploaded bytes, and calculate minimal progress that this peer should have and compare with peer reported progress. If peer reported progress is smaller than our calculated progress too much, we will consider it's cheating. It works well on detecting new various and cheat clients.",
  'page.settings.tab.config.module.progressCheatBlocker.progressRewindDetection':
    'Progress rewind detection',
  'page.settings.tab.config.module.progressCheatBlocker.rewindMaxDifference':
    'Maximum rewind progress difference',
  'page.settings.tab.config.module.progressCheatBlocker.rewindMaxDifference.tips':
    'Sometimes the pisces may break during transfer, client may drop those pisces, we allow client have rewind in reasonable range',
  'page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients':
    'Block excessive clients',
  'page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients.tips':
    'Block clients that download more than the torrent size',
  'page.settings.tab.config.module.progressCheatBlocker.excessive_threshold':
    'Excessive download threshold',
  'page.settings.tab.config.module.progressCheatBlocker.excessive_threshold.tips':
    'IsExcessive = uploaded > (torrent_size * excessive-threshold)',
  'page.settings.tab.config.module.progressCheatBlocker.ipv4prefixlength': 'IPv4 prefix length',
  'page.settings.tab.config.module.progressCheatBlocker.ipv6prefixlength': 'IPv6 prefix length',
  'page.settings.tab.config.module.progressCheatBlocker.ipprefixLength.tips':
    'IPs from the same subnet are considered as the same user',
  'page.settings.tab.config.module.progressCheatBlocker.banDuration': 'Ban Duration',
  'page.settings.tab.config.module.progressCheatBlocker.enablePersist': 'Enable persist recording',
  'page.settings.tab.config.module.progressCheatBlocker.enablePersist.tips':
    'Enable this feature may increase disk I/O and may affect performance, even may cause flash wear on embedded devices',
  'page.settings.tab.config.module.progressCheatBlocker.persistDuration': 'Persist duration',
  'page.settings.tab.config.module.progressCheatBlocker.maxWaitDuration':
    'Max wait duration before ban',
  'page.settings.tab.config.module.progressCheatBlocker.maxWaitDuration.tips':
    "Sometimes due the network issue, the peer may cannot sync the progress information on time, When a Peer reached ban condition, the timer will start and Peer will be banned after timer timed out if Peer's progress not update to excepted value on time ",
  'page.settings.tab.config.module.progressCheatBlocker.enableFastPCBTest': 'Enable fast PCB test',
  'page.settings.tab.config.module.progressCheatBlocker.enableFastPCBTest.tips':
    'This option will allow PCB ban the Peer from downloader for disconnect it, this will heat up progress reset check quickly.',
  'page.settings.tab.config.module.progressCheatBlocker.fastPCBTestPercentage':
    'Fast PCB test threshold',

  'page.settings.tab.config.module.ipAddressBlocker.title': 'IP address/port blacklist',
  'page.settings.tab.config.module.ipAddressBlocker.individualBanTime': 'Use global ban duration',
  'page.settings.tab.config.module.ipAddressBlocker.rules':
    'Configure rules please go to the {link} page',
  'page.settings.tab.config.module.ipAddressBlocker.rules.link': 'Rule Configuration',

  'page.settings.tab.config.module.autoRangeBan.title': 'Range ban',
  'page.settings.tab.config.module.autoRangeBan.tips':
    'After a peer got banned, other connected peers that in same range with banned peers will also get banned.',
  'page.settings.tab.config.module.autoRangeBan.individualBanTime': 'Use global ban duration',
  'page.settings.tab.config.module.autoRangeBan.ipv4Prefix': 'IPv4 prefix length',
  'page.settings.tab.config.module.autoRangeBan.ipv6Prefix': 'IPv6 prefix length',

  'page.settings.tab.config.module.multiDialingBlocker.title': 'Multi-dialing blocker',
  'page.settings.tab.config.module.multiDialingBlocker.individualBanTime':
    'Use global ban duration',
  'page.settings.tab.config.module.multiDialingBlocker.subnet-mask-length': 'Subnet mask length',
  'page.settings.tab.config.module.multiDialingBlocker.subnet-mask-v6-length':
    'IPv6 subnet mask length',
  'page.settings.tab.config.module.multiDialingBlocker.tolerate-num': 'Tolerate number',
  'page.settings.tab.config.module.multiDialingBlocker.tolerate-num.tips':
    'The allowed maximum amount of ips in same subnet, to avoid mistake bans that caused by DHCP re-allocated IPs, or multiple users in same ISP',
  'page.settings.tab.config.module.multiDialingBlocker.timeWindow': 'Detection window',
  'page.settings.tab.config.module.multiDialingBlocker.keep-hunting': 'Keep hunting',
  'page.settings.tab.config.module.multiDialingBlocker.keep-hunting.tips':
    'If a specific IP flagged multi-dialing, ignore the caching span and keep searching other IPs in same subnet',
  'page.settings.tab.config.module.multiDialingBlocker.keep-hunting-time': 'Keep hunting time',

  'page.settings.tab.config.module.expressionEngine.title': 'AviatorScript rule engine',
  'page.settings.tab.config.module.expressionEngine.tips':
    'Enable this feature can use AviatorScript language to write complex rules to achieve more custom functions',
  'page.settings.tab.config.module.expressionEngine.individualBanTime': 'Use global ban duration',

  'page.settings.tab.config.module.ruleSubscribe.title': 'Rule subscribe',
  'page.settings.tab.config.module.ruleSubscribe.individualBanTime': 'Use global ban duration',
  'page.settings.tab.config.module.ruleSubscribe.subscribe':
    'For other configurations, please go to the {link} page',
  'page.settings.tab.config.module.ruleSubscribe.subscribe.link': 'Rule Subscribe',

  'page.settings.tab.config.module.activeMonitoring.title': 'Active monitoring',
  'page.settings.tab.config.module.activeMonitor.disable.tips':
    'This function is required by some charts.',
  'page.settings.tab.config.module.activeMonitor.dataRetentionTime': 'Retention time',
  'page.settings.tab.config.module.activeMonitor.dataRetentionTime.tips':
    "Deleted records won't free the disk space, but new data will reuse those parts of space due SQLite internal design",
  'page.settings.tab.config.module.activeMonitor.dataCleanupInterval': 'Cleanup interval'
}
