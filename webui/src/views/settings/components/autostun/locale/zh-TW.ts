export default {
  'page.settings.tab.autostun': 'AutoSTUN',
  'page.settings.tab.autostun.title': 'AutoSTUN',
  'page.settings.tab.autostun.description':
    'AutoSTUN 可以在 NAT1 網路環境下為 IPv4 協議棧自動進行埠映射和 NAT 打洞。為進一步提升連線性，建議為下載器同時啟用 IPv6 網路。',
  'page.settings.tab.autostun.warning':
    '僅能在 NAT1 網路環境下工作，不支援其他 NAT 類型。對於公網 IP 使用者，建議使用傳統埠映射 + IPv6 雙棧網路方案以獲得最佳連線性。',

  // Enable/Disable section
  'page.settings.tab.autostun.enable': '啟用 AutoSTUN',
  'page.settings.tab.autostun.enable.tips': '啟用後將自動為選擇的下載器建立隧道',
  'page.settings.tab.autostun.friendly_mapping': '啟用友善本地回環位址映射',
  'page.settings.tab.autostun.friendly_mapping.tips':
    '啟用後反向代理伺服器將使用更友善的本地位址映射方式，同時還可解決本機設備上為下載器停用「允許來自同一 IP 位址的多重連線」（保證 PeerBanHelper 工作的必須選項）的副作用',

  // NAT Status section
  'page.settings.tab.autostun.nat_status': 'NAT 狀態',
  'page.settings.tab.autostun.nat_type': 'NAT 類型',
  'page.settings.tab.autostun.nat_type.refresh': '重新整理 NAT 類型',
  'page.settings.tab.autostun.nat_type.refreshing': '已啟動背景 NAT 類型更新任務，請稍候...',
  'page.settings.tab.autostun.nat_type.UdpBlocked': '[/] UDP 被阻擋 (UdpBlocked)',
  'page.settings.tab.autostun.nat_type.OpenInternet': '[NAT0] 開放網際網路 (Open Internet)',
  'page.settings.tab.autostun.nat_type.SymmetricUdpFirewall':
    '[/] 對稱型 UDP 防火牆 (Symmetric UDP Firewall)',
  'page.settings.tab.autostun.nat_type.FullCone': '[NAT1] 完全圓錐型 NAT (FullCone)',
  'page.settings.tab.autostun.nat_type.RestrictedCone': '[NAT2] 受限圓錐型 NAT (RestrictedCone)',
  'page.settings.tab.autostun.nat_type.PortRestrictedCone':
    '[NAT3] 埠受限圓錐型 NAT (PortRestrictedCone)',
  'page.settings.tab.autostun.nat_type.Symmetric': '[NAT4] 對稱型 NAT (Symmetric)',
  'page.settings.tab.autostun.nat_type.Unknown': '未知',
  'page.settings.tab.autostun.nat_compatible': '✅ 相容 AutoSTUN',
  'page.settings.tab.autostun.nat_incompatible': '❌ 不相容 AutoSTUN',

  // Downloader Configuration section
  'page.settings.tab.autostun.downloader_config': '下載器設定',
  'page.settings.tab.autostun.select_downloaders': '選擇要啟用 AutoSTUN 的下載器',
  'page.settings.tab.autostun.no_downloaders': '暫無可用的下載器',
  'page.settings.tab.autostun.save_config': '儲存設定',
  'page.settings.tab.autostun.save_success': '設定儲存成功',
  'page.settings.tab.autostun.save_failed': '設定儲存失敗',

  // Tunnel Information section
  'page.settings.tab.autostun.tunnel_info': '隧道資訊',
  'page.settings.tab.autostun.tunnel_list': '目前隧道列表',
  'page.settings.tab.autostun.no_tunnels': '暫無活動隧道',
  'page.settings.tab.autostun.tunnel_valid': '有效',
  'page.settings.tab.autostun.tunnel_invalid': '無效',
  'page.settings.tab.autostun.tunnel_downloader': '下載器',
  'page.settings.tab.autostun.tunnel_status': '狀態',
  'page.settings.tab.autostun.tunnel_proxy': '代理位址',
  'page.settings.tab.autostun.tunnel_upstream': '上游位址',
  'page.settings.tab.autostun.tunnel_connections': '活動連線',
  'page.settings.tab.autostun.tunnel_stats': '統計資訊',
  'page.settings.tab.autostun.tunnel_handled': '已處理',
  'page.settings.tab.autostun.tunnel_failed': '失敗',
  'page.settings.tab.autostun.tunnel_blocked': '拒絕',
  'page.settings.tab.autostun.tunnel_downstream_bytes': '出站',
  'page.settings.tab.autostun.tunnel_upstream_bytes': '入站',
  'page.settings.tab.autostun.view_connections': '檢視連線表',

  // Connection Table Modal
  'page.settings.tab.autostun.connection_table': '連線表',
  'page.settings.tab.autostun.connection_downstream': '下游位址',
  'page.settings.tab.autostun.connection_proxy': '代理位址',
  'page.settings.tab.autostun.connection_upstream': '上游位址',
  'page.settings.tab.autostun.connection_established': '建立時間',
  'page.settings.tab.autostun.connection_activity': '最後活動',
  'page.settings.tab.autostun.connection_bytes': '傳輸資料',
  'page.settings.tab.autostun.no_connections': '暫無活動連線'
}
