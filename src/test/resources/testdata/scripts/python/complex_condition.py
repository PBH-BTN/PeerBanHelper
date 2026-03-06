# @NAME 复杂条件检查
# @AUTHOR Test
# @CACHEABLE false
# @VERSION 1.0
# @THREADSAFE false

# 综合多个条件进行判断

def is_blank(s):
    return s is None or str(s).strip() == ''

is_client_name_blank = is_blank(clientName)
is_peer_id_blank = is_blank(peerId)

if is_client_name_blank and is_peer_id_blank:
    result = u'@客户端信息不完整，跳过检查'
else:
    # 检测可疑行为的标志
    reason = ''

    # 条件1: 上传速度为0但下载速度很高 (> 1MB/s)
    if uploadSpeed == 0 and downloadSpeed > 1048576:
        reason = u'只下载不上传'

    # 条件2: 进度异常（已经100%但还在下载）
    if reason == '' and progress >= 1.0 and downloadSpeed > 0:
        reason = u'进度100%仍在下载'

    # 条件3: 客户端名称包含可疑关键字
    if reason == '' and not is_client_name_blank:
        client_lower = str(clientName).lower()
        if 'fake' in client_lower or 'hack' in client_lower:
            reason = u'可疑客户端名称'

    if reason != '':
        result = u'可疑行为检测: ' + reason
    else:
        result = False
