export default {
  'page.settings.tab.autostun': 'AutoSTUN',
  'page.settings.tab.autostun.title': 'AutoSTUN Network Penetration',
  'page.settings.tab.autostun.description':
    'AutoSTUN can automatically perform port mapping and NAT hole punching in NAT1 network environments to improve BitTorrent connectivity.',
  'page.settings.tab.autostun.warning':
    'Note: This feature is only recommended when NAT type is FullCone. For users with public IP or other port forwarding tools, traditional solutions are preferred.',

  // Enable/Disable section
  'page.settings.tab.autostun.enable': 'Enable AutoSTUN',
  'page.settings.tab.autostun.enable.tips':
    'When enabled, network penetration services will be automatically provided for selected downloaders',
  'page.settings.tab.autostun.friendly_mapping': 'Enable Friendly Loopback Address Mapping',
  'page.settings.tab.autostun.friendly_mapping.tips':
    'When enabled, the reverse proxy server will use a more friendly local address mapping method, which may improve compatibility',

  // NAT Status section
  'page.settings.tab.autostun.nat_status': 'NAT Status',
  'page.settings.tab.autostun.nat_type': 'NAT Type',
  'page.settings.tab.autostun.nat_type.refresh': 'Refresh NAT Type',
  'page.settings.tab.autostun.nat_type.refreshing': 'Detecting...',
  'page.settings.tab.autostun.nat_type.UdpBlocked': 'UDP Blocked',
  'page.settings.tab.autostun.nat_type.OpenInternet': '[NAT0] Open Internet',
  'page.settings.tab.autostun.nat_type.SymmetricUdpFirewall': 'Symmetric UDP Firewall',
  'page.settings.tab.autostun.nat_type.FullCone': '[NAT1] Full Cone NAT',
  'page.settings.tab.autostun.nat_type.RestrictedCone': '[NAT2] Restricted Cone NAT',
  'page.settings.tab.autostun.nat_type.PortRestrictedCone': '[NAT3] Port Restricted Cone NAT',
  'page.settings.tab.autostun.nat_type.Symmetric': '[NAT4] Symmetric NAT',
  'page.settings.tab.autostun.nat_type.Unknown': 'Unknown',
  'page.settings.tab.autostun.nat_compatible': '✅ Compatible with AutoSTUN',
  'page.settings.tab.autostun.nat_incompatible': '❌ Incompatible with AutoSTUN',

  // Downloader Configuration section
  'page.settings.tab.autostun.downloader_config': 'Downloader Configuration',
  'page.settings.tab.autostun.select_downloaders': 'Select downloaders to enable AutoSTUN',
  'page.settings.tab.autostun.no_downloaders': 'No available downloaders',
  'page.settings.tab.autostun.save_config': 'Save Configuration',
  'page.settings.tab.autostun.save_success': 'Configuration saved successfully',
  'page.settings.tab.autostun.save_failed': 'Failed to save configuration',

  // Tunnel Information section
  'page.settings.tab.autostun.tunnel_info': 'Tunnel Information',
  'page.settings.tab.autostun.tunnel_list': 'Current Tunnel List',
  'page.settings.tab.autostun.no_tunnels': 'No active tunnels',
  'page.settings.tab.autostun.tunnel_valid': 'Valid',
  'page.settings.tab.autostun.tunnel_invalid': 'Invalid',
  'page.settings.tab.autostun.tunnel_downloader': 'Downloader',
  'page.settings.tab.autostun.tunnel_status': 'Status',
  'page.settings.tab.autostun.tunnel_proxy': 'Proxy Address',
  'page.settings.tab.autostun.tunnel_upstream': 'Upstream Address',
  'page.settings.tab.autostun.tunnel_connections': 'Active Connections',
  'page.settings.tab.autostun.tunnel_stats': 'Statistics',
  'page.settings.tab.autostun.tunnel_handled': 'Handled',
  'page.settings.tab.autostun.tunnel_failed': 'Failed',
  'page.settings.tab.autostun.tunnel_blocked': 'Blocked',
  'page.settings.tab.autostun.tunnel_downstream_bytes': 'Downstream',
  'page.settings.tab.autostun.tunnel_upstream_bytes': 'Upstream',
  'page.settings.tab.autostun.view_connections': 'View Connection Table',

  // Connection Table Modal
  'page.settings.tab.autostun.connection_table': 'Connection Table',
  'page.settings.tab.autostun.connection_downstream': 'Downstream Address',
  'page.settings.tab.autostun.connection_proxy': 'Proxy Address',
  'page.settings.tab.autostun.connection_upstream': 'Upstream Address',
  'page.settings.tab.autostun.connection_established': 'Established Time',
  'page.settings.tab.autostun.connection_activity': 'Last Activity',
  'page.settings.tab.autostun.connection_bytes': 'Data Transfer',
  'page.settings.tab.autostun.no_connections': 'No active connections'
}
