# @NAME 上传比例检查
# @AUTHOR Test
# @CACHEABLE true
# @VERSION 1.0
# @THREADSAFE true

# 检查上传/下载比例，识别异常的吸血行为
# 如果下载量很大但上传量很小，可能是吸血客户端

if downloaded is None or downloaded == 0:
    result = False
else:
    if uploaded is None or uploaded == 0:
        ratio = 0.0
    else:
        ratio = float(uploaded) / float(downloaded)

    # 阈值从环境变量获取
    min_ratio = float(minRatioThreshold)
    min_downloaded = long(minDownloadedThreshold)

    # 如果下载量超过阈值且比例过低，则封禁
    if downloaded > min_downloaded and ratio < min_ratio:
        result = u'吸血检测: 下载=' + str(downloaded) + u' 上传=' + str(uploaded) + u' 比例=' + str(ratio)
    else:
        result = False
