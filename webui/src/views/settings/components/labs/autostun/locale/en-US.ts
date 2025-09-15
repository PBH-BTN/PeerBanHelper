export default {
  'page.settings.tab.autostun': 'AutoSTUN',
  'page.settings.tab.autostun.title': 'AutoSTUN',
  'page.settings.tab.autostun.description':
    'AutoSTUN can automatically perform port mapping and NAT traversal for IPv4 protocol stacks in NAT1 network environments. To further enhance connectivity, it is recommended to enable IPv6 networks for the downloader simultaneously.',
  'page.settings.tab.autostun.warning':
    'Works only in NAT1 network environments and does not support other NAT types. For public IP users, it is recommended to use the traditional port mapping + IPv6 dual-stack network solution for optimal connectivity.',

  // Enable/Disable section
  'page.settings.tab.autostun.enable': 'Enable AutoSTUN',
  'page.settings.tab.autostun.enable.tips':
    'Once enabled, tunnels will be automatically created for the selected downloader',
  'page.settings.tab.autostun.friendly_mapping': 'Friendly Address Mapping',
  'page.settings.tab.autostun.friendly_mapping.tips':
    'When enabled, the reverse proxy server will use a more friendly local address mapping method, and it can also resolve the side effects of disabling "Allow multiple connections from the same IP address" (a necessary option for PeerBanHelper to work) on the downloader on the local device.',

  // NAT Status section
  'page.settings.tab.autostun.nat_status': 'NAT Status',
  'page.settings.tab.autostun.nat_type': 'NAT Type',
  'page.settings.tab.autostun.nat_type.refresh': 'Refresh NAT Type',
  'page.settings.tab.autostun.nat_type.refreshing':
    'Background NAT type update task has started, please wait...',
  'page.settings.tab.autostun.nat_type.UdpBlocked': '[/] UDP Blocked (UdpBlocked)',
  'page.settings.tab.autostun.nat_type.OpenInternet': '[NAT0] Open Internet',
  'page.settings.tab.autostun.nat_type.SymmetricUdpFirewall': '[/] Symmetric UDP Firewall',
  'page.settings.tab.autostun.nat_type.FullCone': '[NAT1] Full Cone NAT',
  'page.settings.tab.autostun.nat_type.RestrictedCone': '[NAT2] Restricted Cone NAT',
  'page.settings.tab.autostun.nat_type.PortRestrictedCone': '[NAT3] Port Restricted Cone NAT',
  'page.settings.tab.autostun.nat_type.Symmetric': '[NAT4] Symmetric NAT',
  'page.settings.tab.autostun.nat_type.Unknown': 'Unknown',
  'page.settings.tab.autostun.nat_incompatible': 'NAT Type Incompatible',
  'page.settings.tab.autostun.nat_incompatible.tooltip':
    'Current NAT type does not support AutoSTUN, requires NAT1 (Full Cone) environment',
  'page.settings.tab.autostun.netdriver_incompatible': 'Docker Network Issue',
  'page.settings.tab.autostun.netdriver_incompatible.tooltip':
    'AutoSTUN may not work properly in Docker Bridge network mode',
  'page.settings.tab.autostun.netdriver_compatible': 'Network Driver Compatible',
  'page.settings.tab.autostun.network_driver': 'Network Driver',
  'page.settings.tab.autostun.compatibility': 'Compatibility',
  'page.settings.tab.autostun.compatible': 'Compatible',
  'page.settings.tab.autostun.incompatible': 'Incompatible',

  // Downloader Configuration section
  'page.settings.tab.autostun.downloader_config': 'Downloader Configuration',
  'page.settings.tab.autostun.select_downloaders': 'Selected downloaders',
  'page.settings.tab.autostun.available_downloaders': 'Available',
  'page.settings.tab.autostun.enabled_downloaders': 'Enabled',
  'page.settings.tab.autostun.no_downloaders': 'No available downloaders',
  'page.settings.tab.autostun.save_config': 'Save Configuration',
  'page.settings.tab.autostun.save_success': 'Configuration saved successfully',
  'page.settings.tab.autostun.save_failed': 'Configuration save failed',

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
  'page.settings.tab.autostun.tunnel_downstream_bytes': 'Outbound',
  'page.settings.tab.autostun.tunnel_upstream_bytes': 'Inbound',
  'page.settings.tab.autostun.view_connections': 'View Connection Table',

  // Connection Table Modal
  'page.settings.tab.autostun.region': 'Country/Region',
  'page.settings.tab.autostun.connection_table': 'Connection Table',
  'page.settings.tab.autostun.connection_downstream': 'Downstream Address',
  'page.settings.tab.autostun.connection_proxy': 'Proxy Address',
  'page.settings.tab.autostun.connection_upstream': 'Upstream Address',
  'page.settings.tab.autostun.connection_established': 'Established Time',
  'page.settings.tab.autostun.connection_activity': 'Last Activity',
  'page.settings.tab.autostun.connection_bytes': 'Transferred Data',
  'page.settings.tab.autostun.no_connections': 'No active connections'
}
