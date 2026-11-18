import { ipVersion } from 'is-ip'

function ipv6Words(address: string): number[] {
  // URL normalizes embedded IPv4 octets to hex and compresses the longest zero run.
  const canonical = new URL(`http://[${address}]/`).hostname.slice(1, -1)
  const [left, right] = canonical.split('::')
  const before = left ? left.split(':') : []
  const after = right ? right.split(':') : []
  return [...before, ...Array(8 - before.length - after.length).fill('0'), ...after].map((word) =>
    parseInt(word, 16)
  )
}

export function getCommentScopes(input: string) {
  const [address, length, extra] = input.split('/')
  if (!address || extra !== undefined || address.includes('%')) return []
  const version = ipVersion(address)
  if (!version) return []

  const ipv6 = version === 6
  const bits = ipv6 ? 128 : 32
  const inputPrefix = length === undefined ? bits : Number(length)
  if ((length !== undefined && !/^\d+$/.test(length)) || inputPrefix > bits) return []

  const prefixes = ipv6 ? [64, 60, 56, 52, 50, 48, 32] : [32, 24, 16]
  const words = ipv6 ? ipv6Words(address) : address.split('.').map(Number)
  const wordBits = ipv6 ? 16 : 8
  return prefixes
    .filter((prefix) => prefix <= inputPrefix)
    .map((prefix) => {
      const network = words.map((word, index) => {
        const hostBits = Math.min(wordBits, Math.max(0, (index + 1) * wordBits - prefix))
        return (word >>> hostBits) << hostBits
      })
      const host = ipv6
        ? new URL(`http://[${network.map((word) => word.toString(16)).join(':')}]/`).hostname.slice(
            1,
            -1
          )
        : network.join('.')
      return { prefix, scope: `${host}/${prefix}` }
    })
}
