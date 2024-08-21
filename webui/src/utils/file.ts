export function formatFileSize(bytes: number, decimals = 2) {
  if (bytes === -1) return 'N/A' // 在没有完成元数据拉取之前，部分下载器会返回 -1 值
  if (bytes === 0) return '0 Bytes'

  const k = 1024
  const dm = decimals < 0 ? 0 : decimals
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  if (i >= sizes.length) return 'Too large'
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i]
}
