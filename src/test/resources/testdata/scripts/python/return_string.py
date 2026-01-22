# @NAME 返回字符串测试
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 测试返回不同类型的字符串
# 空字符串 = OK
# @开头 = SKIP
# 其他 = BAN（返回封禁原因）

if returnType == 'empty':
    result = ''
elif returnType == 'skip':
    result = u'@跳过处理'
elif returnType == 'ban':
    result = u'检测到恶意客户端: ' + str(clientName)
else:
    result = ''
