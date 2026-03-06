# @NAME 列表操作测试
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 测试 Python 的列表操作

result = False

# 创建一个检查项列表
checks = []

# 检查客户端是否在白名单中
whitelist = ['qbittorrent', 'transmission', 'deluge', 'rtorrent', 'aria2']

if clientName is not None:
    client_lower = str(clientName).lower()
    is_whitelisted = False
    for w in whitelist:
        if w in client_lower:
            is_whitelisted = True
            break

    if not is_whitelisted:
        checks.append(u'不在白名单中')

# 检查下载速度是否异常
if downloadSpeed is not None and downloadSpeed > 100 * 1024 * 1024:  # 100MB/s
    checks.append(u'下载速度异常高')

# 检查上传速度是否异常
if uploadSpeed is not None and uploadSpeed > 100 * 1024 * 1024:  # 100MB/s
    checks.append(u'上传速度异常高')

# 如果有任何检查项，返回结果
if len(checks) > 0:
    result = u'检测到问题: ' + u'; '.join(checks)
