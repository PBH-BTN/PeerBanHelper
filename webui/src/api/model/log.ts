export interface Log {
  content: string
  offset: number
  thread: string
  time: number
  level: LogLevel
}

export enum LogLevel {
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
  TRACE = 'TRACE'
}
