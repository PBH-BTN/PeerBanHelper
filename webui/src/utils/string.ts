import { isIPv6 } from 'is-ip'
export function hashString(str: string) {
  let hash = 0,
    i,
    chr
  if (str.length === 0) return hash
  for (i = 0; i < str.length; i++) {
    chr = str.charCodeAt(i)
    hash = (hash << 5) - hash + chr
    hash |= 0 // Convert to 32bit integer
  }
  return hash
}

export const formatIPAddressPort = (address: string, port: number) =>
  isIPv6(address) ? `[${address}]:${port}` : `${address}:${port}`
