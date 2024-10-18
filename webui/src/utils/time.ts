export function formatMilliseconds(ms: number): string {
  const days = Math.floor(ms / 86400000)
  ms %= 86400000
  const hours = Math.floor(ms / 3600000)
  ms %= 3600000
  const minutes = Math.floor(ms / 60000)
  ms %= 60000
  const seconds = Math.floor(ms / 1000)

  let result = ''
  if (days > 0) {
    result += `${days} Day${days > 1 ? 's' : ''} `
  }
  if (hours > 0) {
    result += `${hours} Hour${hours > 1 ? 's' : ''} `
  }
  if (minutes > 0) {
    result += `${minutes} Minute${minutes > 1 ? 's' : ''} `
  }
  if (seconds > 0) {
    result += `${seconds} Second${seconds > 1 ? 's' : ''} `
  }

  return result.trim()
}

export function formatSeconds(seconds: number): string {
  return formatMilliseconds(seconds * 1000)
}
