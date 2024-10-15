export default {
  'page.settings.tab.config.base.title': '基础配置',
  'page.settings.tab.config.form.checkInterval': '检查频率',
  'page.settings.tab.config.unit.ms': '毫秒',
  'page.settings.tab.config.form.banDuration': '封禁持续时间',
  'page.settings.tab.config.form.ingoreAddress': '忽略地址',
  'page.settings.tab.config.form.ingoreAddress.tooltip':
    '来自这些 IP 地址的 Peers 不会被 PBH 检查，绕过所有检查规则',
  'page.settings.tab.config.module.title': '模块配置',
  'page.settings.tab.config.module.enable': '启用',

  'page.settings.tab.config.module.peerIdBlackList.if': '如果',
  'page.settings.tab.config.module.peerIdBlackList.hit': '命中',
  'page.settings.tab.config.module.peerIdBlackList.miss': '未命中',

  'page.settings.tab.config.module.banRuleTips.STARTS_WITH': '匹配开头',
  'page.settings.tab.config.module.banRuleTips.ENDS_WITH': '匹配结尾',
  'page.settings.tab.config.module.banRuleTips.LENGTH': '匹配字符串长度',
  'page.settings.tab.config.module.banRuleTips.CONTAINS': '匹配包含',
  'page.settings.tab.config.module.banRuleTips.EQUALS': '匹配相同',
  'page.settings.tab.config.module.banRuleTips.REGEX': '匹配正则表达式',

  'page.settings.tab.config.module.banRuleTips.DEFAULT': '默认',
  'page.settings.tab.config.module.banRuleTips.TRUE': '封禁',
  'page.settings.tab.config.module.banRuleTips.FALSE': '排除',

  'page.settings.tab.config.module.peerIdBlackList': 'PeerId 封禁',
  'page.settings.tab.config.module.peerIdBlackList.individualBanTime': '使用全局封禁时间',
  'page.settings.tab.config.module.peerIdBlackList.banPeerId': '封禁 PeerId',
  'page.settings.tab.config.module.peerIdBlackList.rule': '规则',

  'page.settings.tab.config.module.clientNameBlackList': '客户端名称封禁',

  'page.settings.tab.config.module.progressCheatBlocker': '进度作弊检查器',
  'page.settings.tab.config.module.progressCheatBlocker.tips':
    '有时这会错误的封禁部分启用“超级做种”的客户端。但在大多数情况下，此模块能够有效阻止循环下载的流量消耗器，建议启用。',
  'page.settings.tab.config.module.progressCheatBlocker.minSize': '最小文件大小',
  'page.settings.tab.config.module.progressCheatBlocker.minSize.tips':
    'Torrent 小于此值不进行检查，对等体可能来不及同步正确的下载进度',
  'page.settings.tab.config.module.progressCheatBlocker.maxDifference': '最大进度差异',
  'page.settings.tab.config.module.progressCheatBlocker.maxDifference.tips':
    'PeerBanHelper 根据 BT 客户端记录的向此对等体实际上传的字节数，计算该对等体的最小下载进度，并与对等体汇报给 BT 客户端下载进度进行比较，如果对等体汇报的总体下载进度远远低于我们上传给此对等体的数据量的比例，我们应考虑客户端正在汇报假进度，对于自动识别迅雷、QQ旋风的变种非常有效，能够在不更新规则的情况下自动封禁报假进度的吸血客户端',
  'page.settings.tab.config.module.progressCheatBlocker.progressRewindDetection': '进度倒退检测',
  'page.settings.tab.config.module.progressCheatBlocker.rewindMaxDifference': '最大回退进度差异',
  'page.settings.tab.config.module.progressCheatBlocker.rewindMaxDifference.tips':
    '考虑到有时文件片段在传输时可能因损坏而未通过校验被丢弃，我们允许客户端出现合理的进度倒退',
  'page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients': '禁止过量下载',
  'page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients.tips':
    '禁止那些在同一个种子的累计下载量超过种子本身大小的客户端',
  'page.settings.tab.config.module.progressCheatBlocker.excessive_threshold': '过量下载阈值',
  'page.settings.tab.config.module.progressCheatBlocker.excessive_threshold.tips':
    '计算规则： 是否过量下载 = 上传总大小 > (种子总大小 * excessive-threshold)',
  'page.settings.tab.config.module.progressCheatBlocker.ipv4prefixlength': 'IPv4 前缀长度',
  'page.settings.tab.config.module.progressCheatBlocker.ipv6prefixlength': 'IPv6 前缀长度',
  'page.settings.tab.config.module.progressCheatBlocker.ipprefixLength.tips':
    '来自同一子网的 IP 视为同一用户'
}