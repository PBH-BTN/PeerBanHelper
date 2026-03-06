# @NAME PeerId 验证
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 验证 PeerId 和 ClientName 是否匹配

if peerId is None or str(peerId).strip() == '' or clientName is None or str(clientName).strip() == '':
    result = False
else:
    peer_id_lower = str(peerId).lower()
    client_name_lower = str(clientName).lower()

    result = False

    # qBittorrent 检查
    if client_name_lower.startswith('qbittorrent'):
        if not peer_id_lower.startswith('-qb'):
            result = u'PeerId 伪装检测: 客户端=' + str(clientName) + u', PeerId=' + str(peerId) + u', 期望前缀=-qb'

    # Transmission 检查
    elif client_name_lower.startswith('transmission'):
        if not peer_id_lower.startswith('-tr'):
            result = u'PeerId 伪装检测: 客户端=' + str(clientName) + u', PeerId=' + str(peerId) + u', 期望前缀=-tr'

    # Deluge 检查
    elif client_name_lower.startswith('deluge'):
        if not peer_id_lower.startswith('-de'):
            result = u'PeerId 伪装检测: 客户端=' + str(clientName) + u', PeerId=' + str(peerId) + u', 期望前缀=-de'

    # uTorrent 检查
    elif client_name_lower.startswith('utorrent'):
        if not peer_id_lower.startswith('-ut'):
            result = u'PeerId 伪装检测: 客户端=' + str(clientName) + u', PeerId=' + str(peerId) + u', 期望前缀=-ut'

    # BitComet 检查
    elif client_name_lower.startswith('bitcomet'):
        if not peer_id_lower.startswith('-bc'):
            result = u'PeerId 伪装检测: 客户端=' + str(clientName) + u', PeerId=' + str(peerId) + u', 期望前缀=-bc'
