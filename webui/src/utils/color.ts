import { hashString } from './string'
export const getColor = (value: string) => {
  const hash = Math.abs(hashString(value)) % colorTable.length
  return colorTable[hash]
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
