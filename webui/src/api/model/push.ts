export enum PushType {
  Email = 'smtp',
  PushPlus = 'pushplus',
  ServerChan = 'serverchan',
  Telegram = 'telegram',
  Bark = 'bark',
  PushDeer = 'pushdeer',
  Gotify = 'gotify'
}

export enum SMTPEncryption {
  None = 'NONE',
  StartTLS = 'STARTTLS',
  EnforceStartTLS = 'ENFORCE_STARTTLS',
  SSLTLS = 'SSLTLS'
}

interface SMTPConfigBase {
  port: number
  host: string
  sender: string
  senderName: string
  receivers: string[]
  encryption: SMTPEncryption
  sendPartial: boolean
}

interface SMTPAuthConfig extends SMTPConfigBase {
  auth: true
  username: string
  password: string
}

interface SMTPNoAuthConfig extends SMTPConfigBase {
  auth: false
}

export type SMTPConfig = SMTPAuthConfig | SMTPNoAuthConfig

export interface ServerChanConfig {
  sendKey: string
  channel: string
  openId: string
}

export interface PushPlusConfig {
  token: string
  topic: string
  channel: string
}

export interface TelegramConfig {
  token: string
  chatId: string
}

export interface BarkConfig {
  backend_url: string
  device_key: string
  message_group?: string
}

export interface PushDeerConfig {
  endpoint: string
  pushkey: string
}

export interface GotifyConfig {
  endpoint: string
  priority: number
}

export type PushConfig = {
  name: string
} & (
  | { type: PushType.Email; config: SMTPConfig }
  | { type: PushType.PushPlus; config: PushPlusConfig }
  | { type: PushType.ServerChan; config: ServerChanConfig }
  | { type: PushType.Telegram; config: TelegramConfig }
  | { type: PushType.Bark; config: BarkConfig }
  | { type: PushType.PushDeer; config: PushDeerConfig }
  | { type: PushType.Gotify; config: GotifyConfig }
)
