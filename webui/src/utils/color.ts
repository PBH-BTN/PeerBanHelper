import { hashString } from './string'
export const getColor = (value: string, blacklist?: string[]) => {
  let useColor = colorTable
  if (blacklist) {
    useColor = colorTable.filter((color) => !blacklist.includes(color))
  }
  const hash = Math.abs(hashString(value)) % useColor.length
  return useColor[hash]
}

const colorTable = [
  'red',
  'orangered',
  'orange',
  'gold',
  'lime',
  'green',
  'cyan',
  'blue',
  'arcoblue',
  'purple',
  'pinkpurple',
  'magenta'
]
