# @NAME IP 范围检查
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 检查 IP 是否包含特定特征

result = False

if ipAddress is None or ipAddress.strip() == '':
    result = False
else:
    # 检查 IPv6 特定段
    if ':2e0:61ff:fe' in ipAddress:
        result = '检测到特征 IPv6 段: 2e0:61ff:fe'
    # 检查是否为保留地址
    elif ipAddress.startswith('0.') or ipAddress.startswith('127.'):
        result = '@保留地址，跳过检查'
    # 检查私有地址范围
    elif (ipAddress.startswith('192.168.') or
          ipAddress.startswith('10.') or
          any(ipAddress.startswith('172.' + str(i) + '.') for i in range(16, 32))):
        result = '@私有地址，跳过检查'
