export default {
  'page.dashboard.description': 'This page displays the basic data since PeerBanHelper started.',
  'page.dashboard.clientStatus.title': 'Connected Downloaders',
  'page.dashboard.clientStatus.card.title': 'Downloader Status',
  'page.dashboard.clientStatus.card.status': 'Status',
  'page.dashboard.clientStatus.card.type': 'Type',
  'page.dashboard.clientStatus.card.status.normal': 'Normal',
  'page.dashboard.clientStatus.card.status.error': 'Error',
  'page.dashboard.clientStatus.card.status.unknown': 'Unknown',
  'page.dashboard.clientStatus.card.status.need_take_action': 'Need Take Action',
  'page.dashboard.clientStatus.card.status.torrentNumber': 'Active Torrents',
  'page.dashboard.clientStatus.card.status.peerNumber': 'Connected Peers',
  'page.dashboard.clientStatus.card.lastDelete': 'Last downloader cannot be deleted',
  'page.dashboard.statics.currentStatus': 'Current Status',
  'page.dashboard.statics.checked': 'Total checked',
  'page.dashboard.statics.times': 'times',
  'page.dashboard.statics.banPeer': 'Ban Peers',
  'page.dashboard.statics.unbanPeer': 'Unban Peers',
  'page.dashboard.statics.currentBan': 'Currently banned Peers',
  'page.dashboard.statics.currentBanIps': 'Currently banned IP(s)',
  'page.dashboard.statics.number': '',
  'page.dashboard.torrentList.title': 'Active Torrents',
  'page.dashboard.torrentList.column.name': 'Name',
  'page.dashboard.torrentList.column.size': 'Size',
  'page.dashboard.torrentList.column.hash': 'Hash',
  'page.dashboard.torrentList.column.progress': 'Progress',
  'page.dashboard.torrentList.column.speed': 'Speed',
  'page.dashboard.torrentList.column.view': 'View',
  'page.dashboard.editModal.title.new': 'New Downloader',
  'page.dashboard.editModal.title.edit': 'Edit Downloader',
  'page.dashboard.editModal.label.type': 'Type',
  'page.dashboard.editModal.label.name': 'Name',
  'page.dashboard.editModal.label.endpoint': 'Endpoint',
  'page.dashboard.editModal.label.endpoint.error.invalidSchema':
    'Must start with http:// or https://',
  'page.dashboard.editModal.label.endpoint.error.invalidUrl': 'Invalid URL',
  'page.dashboard.editModal.label.username': 'Username',
  'page.dashboard.editModal.label.password': 'Password',
  'page.dashboard.editModal.label.useBasicAuth': 'Use HTTP Basic Auth',
  'page.dashboard.editModal.label.httpVersion': 'HTTP Version',
  'page.dashboard.editModal.label.httpVersion.description':
    '2.0 is faster, which 1.1 is more compatible',
  'page.dashboard.editModal.label.incrementBan': 'Incremental Ban',
  'page.dashboard.editModal.label.incrementBan.description':
    'Helps to alleviate the downloader pressure when saving the ban list, but may cause the inability to ban Peers on some downloaders',
  'page.dashboard.editModal.label.verifySsl': 'Verify SSL',
  'page.dashboard.editModal.biglybt': 'Please install plugin at {url} first.',
  'page.dashboard.editModal.biglybt.url': 'here',
  'page.dashboard.editModal.transmission.discourage': 'Warning: Use Transmission Adapter is discourage. Frequent starting and stopping of torrents on seeds that are often subject to bans can result in frequent updates to the tracker server, indirectly triggering DoS attacks. This increases the load on the tracker server and may lead to your IP address being banned by the tracker. We encourage you to migrate to other downloaders whenever possible. https://github.com/PBH-BTN/PeerBanHelper/issues/382',
  'page.dashboard.peerList.title': 'Active Peer List for ',
  'page.dashboard.peerList.column.address': 'Address',
  'page.dashboard.peerList.column.port': 'Port',
  'page.dashboard.peerList.column.clientName': 'Client Name',
  'page.dashboard.peerList.column.flag': 'Flags',
  'page.dashboard.peerList.column.speed': 'Speed',
  'page.dashboard.peerList.column.uploadedDownloaded': 'Uploaded/Downloaded',
  'page.dashboard.peerList.column.progress': 'Progress',
  'page.dashboard.peerList.column.flags.P': 'μtp',
  'page.dashboard.peerList.column.flags.D': 'Currently downloading (interested and not choked)',
  'page.dashboard.peerList.column.flags.d':
    "Your client wants to download, but peer doesn't want to send (interested and choked)",
  'page.dashboard.peerList.column.flags.U': 'Currently uploading (interested and not choked)',
  'page.dashboard.peerList.column.flags.u':
    "Peer wants your client to upload, but your client doesn't want to (interested and choked)",
  'page.dashboard.peerList.column.flags.O': 'Optimistic unchoke',
  'page.dashboard.peerList.column.flags.S': 'Peer is snubbed',
  'page.dashboard.peerList.column.flags.I': 'Peer is an incoming connection',
  'page.dashboard.peerList.column.flags.K':
    'Peer is unchoking your client, but your client is not interested',
  'page.dashboard.peerList.column.flags.?':
    'Your client unchoked the peer but the peer is not interested',
  'page.dashboard.peerList.column.flags.X':
    'Peer was included in peerlists obtained through Peer Exchange (PEX)',
  'page.dashboard.peerList.column.flags.H': 'Peer was obtained through DHT.',
  'page.dashboard.peerList.column.flags.E': 'Peer is using Protocol Encryption (all traffic)',
  'page.dashboard.peerList.column.flags.e': 'Peer is using Protocol Encryption (handshake)',
  'page.dashboard.peerList.column.flags.L':
    'Peer is local (discovered through network broadcast, or in reserved local IP ranges)'
}
