export default {
  'page.settings.tab.profile.save': '保存',
  'page.settings.tab.profile.base.title': '全局配置',
  'page.settings.tab.profile.form.checkInterval': '检查频率',
  'page.settings.tab.profile.unit.ms': '毫秒',
  'page.settings.tab.profile.unit.s': '秒',
  'page.settings.tab.profile.unit.bytes': '字节',
  'page.settings.tab.profile.form.banDuration': '使用全局封禁时间',
  'page.settings.tab.profile.form.ignoreAddress': '忽略地址',
  'page.settings.tab.profile.form.ignoreAddress.action': '点击查看',
  'page.settings.tab.profile.form.ignoreAddress.tooltip':
    '来自这些 IP 地址的 Peers 不会被 PBH 检查，绕过所有检查规则',
  'page.settings.tab.profile.module.title': '模块配置',
  'page.settings.tab.profile.module.enable': '启用',

  'page.settings.tab.profile.formArray.emptyTips': '请填写非空字符串',

  'page.settings.tab.profile.module.peerIdBlackList.if': '如果',
  'page.settings.tab.profile.module.peerIdBlackList.hit': '命中',
  'page.settings.tab.profile.module.peerIdBlackList.miss': '未命中',

  'page.settings.tab.profile.module.banRuleTips.STARTS_WITH': '匹配开头',
  'page.settings.tab.profile.module.banRuleTips.ENDS_WITH': '匹配结尾',
  'page.settings.tab.profile.module.banRuleTips.LENGTH': '匹配字符串长度',
  'page.settings.tab.profile.module.banRuleTips.CONTAINS': '匹配包含',
  'page.settings.tab.profile.module.banRuleTips.EQUALS': '匹配相同',
  'page.settings.tab.profile.module.banRuleTips.REGEX': '匹配正则表达式',

  'page.settings.tab.profile.module.banRuleTips.DEFAULT': '默认',
  'page.settings.tab.profile.module.banRuleTips.TRUE': '封禁',
  'page.settings.tab.profile.module.banRuleTips.FALSE': '排除',

  'page.settings.tab.profile.module.banRuleTips.empty': '存在为空的条目',

  'page.settings.tab.profile.module.peerIdBlackList': 'PeerID 封禁',
  'page.settings.tab.profile.module.peerIdBlackList.useGlobalBanTime': '使用全局封禁时间',
  'page.settings.tab.profile.module.peerIdBlackList.banPeerId': '封禁 PeerID',
  'page.settings.tab.profile.module.peerIdBlackList.rule': '规则',

  'page.settings.tab.profile.module.clientNameBlackList': '客户端名称封禁',
  'page.settings.tab.profile.module.clientNameBlackList.useGlobalBanTime': '使用全局封禁时间',
  'page.settings.tab.profile.module.clientNameBlackList.banClientName': '封禁客户端名称',
  'page.settings.tab.profile.module.clientNameBlackList.rule': '规则',

  'page.settings.tab.profile.module.progressCheatBlocker': '进度作弊检查',
  'page.settings.tab.profile.module.progressCheatBlocker.tips':
    '有时这会错误的封禁部分启用“超级做种”的客户端。但在大多数情况下，此模块能够有效阻止循环下载的流量消耗器，建议启用。',
  'page.settings.tab.profile.module.progressCheatBlocker.minSize': '最小文件大小',
  'page.settings.tab.profile.module.progressCheatBlocker.minSize.tips':
    'Torrent 小于此值不进行检查，对等体可能来不及同步正确的下载进度',
  'page.settings.tab.profile.module.progressCheatBlocker.maxDifference': '最大进度差异',
  'page.settings.tab.profile.module.progressCheatBlocker.maxDifference.tips':
    'PeerBanHelper 根据 BT 客户端记录的向此对等体实际上传的字节数，计算该对等体的最小下载进度，并与对等体汇报给 BT 客户端下载进度进行比较，如果对等体汇报的总体下载进度远远低于我们上传给此对等体的数据量的比例，我们应考虑客户端正在汇报假进度，对于自动识别迅雷、QQ旋风的变种非常有效，能够在不更新规则的情况下自动封禁报假进度的吸血客户端',
  'page.settings.tab.profile.module.progressCheatBlocker.progressRewindDetection': '进度倒退检测',
  'page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference': '最大回退进度差异',
  'page.settings.tab.profile.module.progressCheatBlocker.rewindMaxDifference.tips':
    '考虑到有时文件片段在传输时可能因损坏而未通过校验被丢弃，我们允许客户端出现合理的进度倒退',
  'page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients': '禁止过量下载',
  'page.settings.tab.profile.module.progressCheatBlocker.block_excessive_clients.tips':
    '禁止那些在同一个种子的累计下载量超过种子本身大小的客户端',
  'page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold': '过量下载阈值',
  'page.settings.tab.profile.module.progressCheatBlocker.excessive_threshold.tips':
    '计算规则： 是否过量下载 = 上传总大小 > (种子总大小 * excessive_threshold)',
  'page.settings.tab.profile.module.progressCheatBlocker.ipv4prefixlength': 'IPv4 前缀长度',
  'page.settings.tab.profile.module.progressCheatBlocker.ipv6prefixlength': 'IPv6 前缀长度',
  'page.settings.tab.profile.module.progressCheatBlocker.ipprefixLength.tips':
    '来自同一子网的 IP 视为同一用户',
  'page.settings.tab.profile.module.progressCheatBlocker.banDuration': '封禁持续时间',
  'page.settings.tab.profile.module.progressCheatBlocker.enablePersist': '启用持久化记录',
  'page.settings.tab.profile.module.progressCheatBlocker.enablePersist.tips':
    '启用此功能可能增加磁盘 I/O 并可能影响性能，嵌入式设备上甚至可能带来闪存磨损',
  'page.settings.tab.profile.module.progressCheatBlocker.persistDuration': '持久化时间',
  'page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration': '封禁前等待时间',
  'page.settings.tab.profile.module.progressCheatBlocker.maxWaitDuration.tips':
    '有时由于下载器网络原因，Peer 可能无法及时同步其进度信息，当 Peer 达到封禁阈值后开始计时，如果 Peer 未在给定时间内更新自己的进度到正常水平，则将被封禁',
  'page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest': '启用快速 PCB 测试',
  'page.settings.tab.profile.module.progressCheatBlocker.enableFastPCBTest.tips':
    '此选项将允许 PCB 在 Peer 下载指定量的数据后，将其短暂的封禁一段时间以便断开其连接，这有助于快速预热进度重置检查',
  'page.settings.tab.profile.module.progressCheatBlocker.fastPCBTestPercentage':
    '快速 PCB 测试启动阈值',

  'page.settings.tab.profile.module.ipAddressBlocker.title': 'IP 地址封禁',
  'page.settings.tab.profile.module.ipAddressBlocker.useGlobalBanTime': '使用全局封禁时间',
  'page.settings.tab.profile.module.ipAddressBlocker.rules': '配置规则请前往{link}页面',
  'page.settings.tab.profile.module.ipAddressBlocker.rules.link': '规则配置',

  'page.settings.tab.profile.module.autoRangeBan.title': '连锁封禁',
  'page.settings.tab.profile.module.autoRangeBan.tips':
    '在封禁 Peer 后，被封禁的 Peer 所在 IP 地址的指定前缀长度内的其它 IP 地址都将一同连锁封禁',
  'page.settings.tab.profile.module.autoRangeBan.useGlobalBanTime': '使用全局封禁时间',
  'page.settings.tab.profile.module.autoRangeBan.ipv4Prefix': 'IPv4 前缀长度',
  'page.settings.tab.profile.module.autoRangeBan.ipv6Prefix': 'IPv6 前缀长度',

  'page.settings.tab.profile.module.multiDialingBlocker.title': '多拨封禁',
  'page.settings.tab.profile.module.multiDialingBlocker.useGlobalBanTime': '使用全局封禁时间',

  'page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-length': '子网掩码长度',
  'page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-v6-length': 'IPv6子网掩码长度',
  'page.settings.tab.profile.module.multiDialingBlocker.tolerate-num': '封禁阈值（{version}）',
  'page.settings.tab.profile.module.multiDialingBlocker.tolerate-num.tips':
    '容许同一网段下载同一种子的IP数量，防止DHCP重新分配IP、碰巧有同一小区的用户下载同一种子等导致的误判',
  'page.settings.tab.profile.module.multiDialingBlocker.timeWindow': '检测窗口',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting': '是否追猎',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting.tips':
    '如果某IP已判定为多拨，无视缓存时间限制继续搜寻其同伙',
  'page.settings.tab.profile.module.multiDialingBlocker.keep-hunting-time': '追猎时间',

  'page.settings.tab.profile.module.expressionEngine.title': 'AviatorScript 规则引擎',
  'page.settings.tab.profile.module.expressionEngine.tips':
    '启用此功能可以使用 AviatorScript 语言编写复杂的规则，以实现更多的自定义功能',
  'page.settings.tab.profile.module.expressionEngine.useGlobalBanTime': '使用全局封禁时间',

  'page.settings.tab.profile.module.ruleSubscribe.title': '规则订阅',
  'page.settings.tab.profile.module.ruleSubscribe.useGlobalBanTime': '使用全局封禁时间',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe': '其他配置请前往{link}页面',
  'page.settings.tab.profile.module.ruleSubscribe.subscribe.link': '规则订阅',

  'page.settings.tab.profile.module.activeMonitor.title': '主动监控',
  'page.settings.tab.profile.module.activeMonitor.disable.tips': '关闭后部分图表功能不可用',
  'page.settings.tab.profile.module.activeMonitor.dataRetentionTime': '数据记录周期',
  'page.settings.tab.profile.module.activeMonitor.dataRetentionTime.tips':
    'SQLite 的特性，记录被删除后不会释放磁盘空间，但后续新数据记录会重新利用此部分空间',
  'page.settings.tab.profile.module.activeMonitor.dataCleanupInterval': '清理周期',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.enable': '开启流量告警',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.tips':
    '设置流量告警阈值，当超出阈值后将发送告警信息提醒您检查下载器状态',
  'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.value': '每日流量告警阈值'
}
