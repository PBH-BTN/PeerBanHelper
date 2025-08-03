export default {
  'page.settings.tab.autostun': 'AutoSTUN',
  'page.settings.tab.autostun.title': 'AutoSTUN 网络穿透',
  'page.settings.tab.autostun.description':
    'AutoSTUN 可以在 NAT1 网络环境下自动进行端口映射和 NAT 打洞，提高 BitTorrent 连通性。',
  'page.settings.tab.autostun.warning':
    '注意：仅建议在 NAT 类型为 FullCone（完全圆锥型NAT）时启用此功能。对于已有公网 IP 或使用其他端口转发工具的用户，建议优先使用传统方案。',

  // Enable/Disable section
  'page.settings.tab.autostun.enable': '启用 AutoSTUN',
  'page.settings.tab.autostun.enable.tips': '启用后将自动为选择的下载器提供网络穿透服务',
  'page.settings.tab.autostun.friendly_mapping': '启用友好本地回环地址映射',
  'page.settings.tab.autostun.friendly_mapping.tips':
    '启用后反向代理服务器将使用更友好的本地地址映射方式，可能提高兼容性',

  // NAT Status section
  'page.settings.tab.autostun.nat_status': 'NAT 状态',
  'page.settings.tab.autostun.nat_type': 'NAT 类型',
  'page.settings.tab.autostun.nat_type.refresh': '刷新 NAT 类型',
  'page.settings.tab.autostun.nat_type.refreshing': '正在检测...',
  'page.settings.tab.autostun.nat_type.UdpBlocked': 'UDP 被阻止',
  'page.settings.tab.autostun.nat_type.OpenInternet': '[NAT0] 开放互联网',
  'page.settings.tab.autostun.nat_type.SymmetricUdpFirewall': '对称型 UDP 防火墙',
  'page.settings.tab.autostun.nat_type.FullCone': '[NAT1] 完全圆锥型 NAT',
  'page.settings.tab.autostun.nat_type.RestrictedCone': '[NAT2] 受限圆锥型 NAT',
  'page.settings.tab.autostun.nat_type.PortRestrictedCone': '[NAT3] 端口受限圆锥型 NAT',
  'page.settings.tab.autostun.nat_type.Symmetric': '[NAT4] 对称型 NAT',
  'page.settings.tab.autostun.nat_type.Unknown': '未知',
  'page.settings.tab.autostun.nat_compatible': '✅ 兼容 AutoSTUN',
  'page.settings.tab.autostun.nat_incompatible': '❌ 不兼容 AutoSTUN',

  // Downloader Configuration section
  'page.settings.tab.autostun.downloader_config': '下载器配置',
  'page.settings.tab.autostun.select_downloaders': '选择要启用 AutoSTUN 的下载器',
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
  'page.settings.tab.autostun.tunnel_downstream_bytes': '下行',
  'page.settings.tab.autostun.tunnel_upstream_bytes': '上行',
  'page.settings.tab.autostun.view_connections': '查看连接表',

  // Connection Table Modal
  'page.settings.tab.autostun.connection_table': '连接表',
  'page.settings.tab.autostun.connection_downstream': '下游地址',
  'page.settings.tab.autostun.connection_proxy': '代理地址',
  'page.settings.tab.autostun.connection_upstream': '上游地址',
  'page.settings.tab.autostun.connection_established': '建立时间',
  'page.settings.tab.autostun.connection_activity': '最后活动',
  'page.settings.tab.autostun.connection_bytes': '传输数据',
  'page.settings.tab.autostun.no_connections': '暂无活动连接',

  // Units
  'page.settings.tab.autostun.bytes': '字节',
  'page.settings.tab.autostun.kb': 'KB',
  'page.settings.tab.autostun.mb': 'MB',
  'page.settings.tab.autostun.gb': 'GB'
}
