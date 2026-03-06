# @NAME 字典操作测试
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 测试 Python 的字典操作

result = False

# 创建客户端特征列表（使用简单的判断代替字典遍历以兼容 Jython）
if clientName is not None and peerId is not None:
    client_lower = str(clientName).lower()
    peer_id_lower = str(peerId).lower()

    # XunLei - 不信任
    if 'xunlei' in client_lower:
        result = u'不信任的客户端: ' + str(clientName)
    # Thunder - 不信任
    elif 'thunder' in client_lower:
        result = u'不信任的客户端: ' + str(clientName)
    # qBittorrent - 信任，检查 PeerId
    elif 'qbittorrent' in client_lower:
        if not peer_id_lower.startswith('-qb'):
            result = u'PeerId 不匹配: 期望 -qb, 实际 ' + str(peerId)
    # Transmission - 信任，检查 PeerId
    elif 'transmission' in client_lower:
        if not peer_id_lower.startswith('-tr'):
            result = u'PeerId 不匹配: 期望 -tr, 实际 ' + str(peerId)
    # uTorrent - 信任，检查 PeerId
    elif 'utorrent' in client_lower:
        if not peer_id_lower.startswith('-ut'):
            result = u'PeerId 不匹配: 期望 -ut, 实际 ' + str(peerId)
