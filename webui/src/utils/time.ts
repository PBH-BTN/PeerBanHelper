import dayjs from 'dayjs'
import Duration from 'dayjs/plugin/duration'
import RelativeTime from 'dayjs/plugin/relativeTime'
dayjs.extend(RelativeTime)
dayjs.extend(Duration)
export function formatMilliseconds(ms: number): string {
  return dayjs.duration({ milliseconds: ms }).humanize()
}

export function formatSeconds(seconds: number): string {
  return dayjs.duration({ seconds: seconds }).humanize()
}
