export default {
  'page.settings.tab.autostun': 'AutoSTUN',
  'page.settings.tab.autostun.title': 'AutoSTUN 網絡穿透',
  'page.settings.tab.autostun.description':
    'AutoSTUN 可以在 NAT1 網絡環境下自動進行端口映射和 NAT 打洞，提高 BitTorrent 連通性。',
  'page.settings.tab.autostun.warning':
    '注意：僅建議在 NAT 類型為 FullCone（完全圓錐型NAT）時啟用此功能。對於已有公網 IP 或使用其他端口轉發工具的用戶，建議優先使用傳統方案。',

  // Enable/Disable section
  'page.settings.tab.autostun.enable': '啟用 AutoSTUN',
  'page.settings.tab.autostun.enable.tips': '啟用後將自動為選擇的下載器提供網絡穿透服務',
  'page.settings.tab.autostun.friendly_mapping': '啟用友好本地回環地址映射',
  'page.settings.tab.autostun.friendly_mapping.tips':
    '啟用後反向代理服務器將使用更友好的本地地址映射方式，可能提高兼容性',

  // NAT Status section
  'page.settings.tab.autostun.nat_status': 'NAT 狀態',
  'page.settings.tab.autostun.nat_type': 'NAT 類型',
  'page.settings.tab.autostun.nat_type.refresh': '刷新 NAT 類型',
  'page.settings.tab.autostun.nat_type.refreshing': '正在檢測...',
  'page.settings.tab.autostun.nat_type.UdpBlocked': 'UDP 被阻止',
  'page.settings.tab.autostun.nat_type.OpenInternet': '[NAT0] 開放互聯網',
  'page.settings.tab.autostun.nat_type.SymmetricUdpFirewall': '對稱型 UDP 防火牆',
  'page.settings.tab.autostun.nat_type.FullCone': '[NAT1] 完全圓錐型 NAT',
  'page.settings.tab.autostun.nat_type.RestrictedCone': '[NAT2] 受限圓錐型 NAT',
  'page.settings.tab.autostun.nat_type.PortRestrictedCone': '[NAT3] 端口受限圓錐型 NAT',
  'page.settings.tab.autostun.nat_type.Symmetric': '[NAT4] 對稱型 NAT',
  'page.settings.tab.autostun.nat_type.Unknown': '未知',
  'page.settings.tab.autostun.nat_compatible': '✅ 兼容 AutoSTUN',
  'page.settings.tab.autostun.nat_incompatible': '❌ 不兼容 AutoSTUN',

  // Downloader Configuration section
  'page.settings.tab.autostun.downloader_config': '下載器配置',
  'page.settings.tab.autostun.select_downloaders': '選擇要啟用 AutoSTUN 的下載器',
  'page.settings.tab.autostun.no_downloaders': '暫無可用的下載器',
  'page.settings.tab.autostun.save_config': '保存配置',
  'page.settings.tab.autostun.save_success': '配置保存成功',
  'page.settings.tab.autostun.save_failed': '配置保存失敗',

  // Tunnel Information section
  'page.settings.tab.autostun.tunnel_info': '隧道信息',
  'page.settings.tab.autostun.tunnel_list': '當前隧道列表',
  'page.settings.tab.autostun.no_tunnels': '暫無活動隧道',
  'page.settings.tab.autostun.tunnel_valid': '有效',
  'page.settings.tab.autostun.tunnel_invalid': '無效',
  'page.settings.tab.autostun.tunnel_downloader': '下載器',
  'page.settings.tab.autostun.tunnel_status': '狀態',
  'page.settings.tab.autostun.tunnel_proxy': '代理地址',
  'page.settings.tab.autostun.tunnel_upstream': '上游地址',
  'page.settings.tab.autostun.tunnel_connections': '活動連接',
  'page.settings.tab.autostun.tunnel_stats': '統計信息',
  'page.settings.tab.autostun.tunnel_handled': '已處理',
  'page.settings.tab.autostun.tunnel_failed': '失敗',
  'page.settings.tab.autostun.tunnel_blocked': '拒絕',
  'page.settings.tab.autostun.tunnel_downstream_bytes': '下行',
  'page.settings.tab.autostun.tunnel_upstream_bytes': '上行',
  'page.settings.tab.autostun.view_connections': '查看連接表',

  // Connection Table Modal
  'page.settings.tab.autostun.connection_table': '連接表',
  'page.settings.tab.autostun.connection_downstream': '下游地址',
  'page.settings.tab.autostun.connection_proxy': '代理地址',
  'page.settings.tab.autostun.connection_upstream': '上游地址',
  'page.settings.tab.autostun.connection_established': '建立時間',
  'page.settings.tab.autostun.connection_activity': '最後活動',
  'page.settings.tab.autostun.connection_bytes': '傳輸數據',
  'page.settings.tab.autostun.no_connections': '暫無活動連接'
}
