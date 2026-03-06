# @NAME 返回数字测试
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 根据输入参数返回不同的数字
# 0 = 不做任何操作
# 1 = 封禁
# 2 = 跳过

if action == 'ban':
    result = 1
elif action == 'skip':
    result = 2
else:
    result = 0
