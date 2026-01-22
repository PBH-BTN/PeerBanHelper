# @NAME 客户端名称检查
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 检查客户端名称是否在黑名单中

if clientName is None or str(clientName).strip() == '':
    result = False
else:
    client_name_lower = str(clientName).lower()

    # 黑名单客户端
    blacklist = ['xunlei', 'thunder', 'baidu', 'flashget']

    found = False
    for name in blacklist:
        if name in client_name_lower:
            result = u'检测到黑名单客户端: ' + str(clientName)
            found = True
            break

    if not found:
        result = False
