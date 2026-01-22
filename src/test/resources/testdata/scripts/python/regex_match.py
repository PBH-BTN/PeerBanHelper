# @NAME 正则表达式匹配测试
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

import re

# 使用正则表达式检查客户端名称和 PeerId

result = False

if clientName is not None and str(clientName).strip() != '':
    client_name_str = str(clientName)

    # 检查是否为可疑的随机字符串（只有字母数字且长度异常）
    if re.match(r'^[a-zA-Z0-9]{20,}$', client_name_str):
        result = u'可疑随机客户端名称: ' + client_name_str
    # 检查 PeerId 格式
    elif peerId is not None and str(peerId).strip() != '':
        peer_id_str = str(peerId)
        # 标准 PeerId 格式通常以 - 开头，后跟两个字母
        if not re.match(r'^-[A-Za-z]{2}', peer_id_str):
            # 检查是否为其他已知格式
            if not re.match(r'^[A-Za-z]{2}\d', peer_id_str) and not re.match(r'^M\d-', peer_id_str):
                result = u'非标准 PeerId 格式: ' + peer_id_str
