export default {
  'page.settings.tab.autostun': 'AutoSTUN',
  'page.settings.tab.autostun.title': 'AutoSTUN',
  'page.settings.tab.autostun.description':
    'AutoSTUN 可以在 NAT1 网络环境下为 IPv4 协议栈自动进行端口映射和 NAT 打洞。为进一步提升连接性，建议为下载器同时启用 IPv6 网络。',
  'page.settings.tab.autostun.warning':
    '仅能在 NAT1 网络环境下工作，不支持其它 NAT 类型。对于公网 IP 用户，建议使用传统端口映射 + IPv6 双栈网络方案以获得最佳连接性。',

  // Enable/Disable section
  'page.settings.tab.autostun.enable': '启用 AutoSTUN',
  'page.settings.tab.autostun.enable.tips': '启用后将自动为选择的下载器创建隧道',
  'page.settings.tab.autostun.friendly_mapping': '友好地址映射',
  'page.settings.tab.autostun.friendly_mapping.tips':
    '启用后反向代理服务器将使用更友好的本地地址映射方式，同时还可解决本机设备上为下载器禁用 “允许来自同一 IP 地址的多重连接”(保证 PeerBanHelper 工作的必须选项) 的副作用',

  // NAT Status section
  'page.settings.tab.autostun.nat_status': 'NAT 状态',
  'page.settings.tab.autostun.nat_type': 'NAT 类型',
  'page.settings.tab.autostun.nat_type.refresh': '刷新 NAT 类型',
  'page.settings.tab.autostun.nat_type.refreshing': '已启动后台 NAT 类型更新任务，请稍等...',
  'page.settings.tab.autostun.nat_type.UdpBlocked': '[/] UDP 被阻止 (UdpBlocked)',
  'page.settings.tab.autostun.nat_type.OpenInternet': '[NAT0] 开放互联网 (Open Internet)',
  'page.settings.tab.autostun.nat_type.SymmetricUdpFirewall':
    '[/] 对称型 UDP 防火墙 (Symmetric UDP Firewall)',
  'page.settings.tab.autostun.nat_type.FullCone': '[NAT1] 完全圆锥型 NAT (FullCone)',
  'page.settings.tab.autostun.nat_type.RestrictedCone': '[NAT2] 受限圆锥型 NAT (RestrictedCone)',
  'page.settings.tab.autostun.nat_type.PortRestrictedCone':
    '[NAT3] 端口受限圆锥型 NAT (PortRestrictedCone)',
  'page.settings.tab.autostun.nat_type.Symmetric': '[NAT4] 对称型 NAT (Symmetric)',
  'page.settings.tab.autostun.nat_type.Unknown': '未知',
  'page.settings.tab.autostun.nat_incompatible': 'NAT 类型不兼容',
  'page.settings.tab.autostun.nat_incompatible.tooltip':
    '当前 NAT 类型不支持 AutoSTUN 功能，需要 NAT1 (完全圆锥型) 环境',
  'page.settings.tab.autostun.netdriver_incompatible': 'Docker 网络配置问题',
  'page.settings.tab.autostun.netdriver_incompatible.tooltip':
    'Docker Bridge 网络模式下 AutoSTUN 可能无法正常工作',
  'page.settings.tab.autostun.netdriver_compatible': '网络驱动兼容',
  'page.settings.tab.autostun.network_driver': '网络驱动',
  'page.settings.tab.autostun.compatibility': '兼容性',
  'page.settings.tab.autostun.compatible': '兼容',
  'page.settings.tab.autostun.incompatible': '不兼容',

  // Downloader Configuration section
  'page.settings.tab.autostun.downloader_config': '下载器配置',
  'page.settings.tab.autostun.select_downloaders': '启用的下载器',
  'page.settings.tab.autostun.available_downloaders': '可用',
  'page.settings.tab.autostun.enabled_downloaders': '启用',
  'page.settings.tab.autostun.no_downloaders': '暂无可用的下载器',
  'page.settings.tab.autostun.save_config': '保存配置',
  'page.settings.tab.autostun.save_success': '配置保存成功',
  'page.settings.tab.autostun.save_failed': '配置保存失败',

  // Tunnel Information section
  'page.settings.tab.autostun.tunnel_info': '隧道信息',
  'page.settings.tab.autostun.tunnel_list': '当前隧道列表',
  'page.settings.tab.autostun.no_tunnels': '暂无活动隧道',
  'page.settings.tab.autostun.tunnel_valid': '有效',
  'page.settings.tab.autostun.tunnel_invalid': '无效',
  'page.settings.tab.autostun.tunnel_downloader': '下载器',
  'page.settings.tab.autostun.tunnel_status': '状态',
  'page.settings.tab.autostun.tunnel_proxy': '代理地址',
  'page.settings.tab.autostun.tunnel_upstream': '上游地址',
  'page.settings.tab.autostun.tunnel_connections': '活动连接',
  'page.settings.tab.autostun.tunnel_stats': '统计信息',
  'page.settings.tab.autostun.tunnel_handled': '已处理',
  'page.settings.tab.autostun.tunnel_failed': '失败',
  'page.settings.tab.autostun.tunnel_blocked': '拒绝',
  'page.settings.tab.autostun.tunnel_downstream_bytes': '出站',
  'page.settings.tab.autostun.tunnel_upstream_bytes': '入站',
  'page.settings.tab.autostun.view_connections': '查看连接表',

  // Connection Table Modal
  'page.settings.tab.autostun.region': '国家/地区',
  'page.settings.tab.autostun.connection_table': '连接表',
  'page.settings.tab.autostun.connection_downstream': '下游地址',
  'page.settings.tab.autostun.connection_proxy': '代理地址',
  'page.settings.tab.autostun.connection_upstream': '上游地址',
  'page.settings.tab.autostun.connection_established': '建立时间',
  'page.settings.tab.autostun.connection_activity': '最后活动',
  'page.settings.tab.autostun.connection_bytes': '传输数据',
  'page.settings.tab.autostun.no_connections': '暂无活动连接'
}
