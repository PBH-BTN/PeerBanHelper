import { DurationFormat } from '@formatjs/intl-durationformat'

function parseTimeDuration(ms: number) {
  const nanoseconds = (ms * 1e6) % 1e3
  const microseconds = Math.floor(ms * 1e3) % 1e3
  const milliseconds = Math.floor(ms) % 1e3

  const totalSeconds = Math.floor(ms / 1000)
  const seconds = totalSeconds % 60

  const totalMinutes = Math.floor(totalSeconds / 60)
  const minutes = totalMinutes % 60

  const totalHours = Math.floor(totalMinutes / 60)
  const hours = totalHours % 24

  const totalDays = Math.floor(totalHours / 24)
  const days = totalDays % 7

  const totalWeeks = Math.floor(totalDays / 7)

  return {
    weeks: totalWeeks,
    days,
    hours,
    minutes,
    seconds,
    milliseconds,
    microseconds,
    nanoseconds
  }
}
function getFormatter(): DurationFormat {
  // @ts-expect-error Supported on chrome 129
  if (Intl.DurationFormat !== undefined) {
    // @ts-expect-error Supported on chrome 129
    return new Intl.DurationFormat()
  } else {
    return new DurationFormat()
  }
}

export function formatMilliseconds(ms: number): string {
  const formatter = getFormatter()
  return formatter.format(parseTimeDuration(ms))
}

export function formatSeconds(seconds: number): string {
  return formatMilliseconds(seconds * 1000)
}
