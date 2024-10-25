import type { CommonResponse } from '@/api/model/common'
import type { Log } from '@/api/model/log'
import urlJoin from 'url-join'
export class StreamLogger {
  private _isOpen: boolean = false

  get isOpen(): boolean {
    return this._isOpen
  }

  get State() {
    return this.ws?.readyState ?? WebSocket.CLOSED
  }

  private ws?: WebSocket

  private url: URL

  constructor(endpoint: string, token: string) {
    const url = new URL(endpoint)
    const protocal = url.protocol === 'https:' ? 'wss' : 'ws'
    this.url = new URL(urlJoin(`${protocal}://${url.host}${url.pathname}`, '/api/log/stream'))
    this.url.searchParams.append('token', token)
  }

  open(
    offset: number,
    callback: (data: Log) => unknown,
    errroCallback: (e: Error) => void
  ): boolean {
    try {
      this.ws?.close() // 关闭旧链接
      this.url.searchParams.append('offset', offset.toString())
      this.ws = new WebSocket(this.url)
      this.ws.onopen = () => {
        this._isOpen = true
      }
      this.ws.onerror = (e) => {
        errroCallback(new Error(`WebSocket error: ${e}`))
      }
      this.ws.onclose = () => {
        this._isOpen = false
      }
      this.ws.onmessage = (event) => {
        try {
          const log: CommonResponse<Log> = JSON.parse(event.data)
          if (log.success) {
            callback(event.data)
          } else {
            errroCallback(new Error(`WebSocket error: ${log.message}`))
          }
        } catch (_) {
          console.log('Drop invalid JSON: ', event.data)
        }
      }
    } catch (e) {
      if (e instanceof Error) errroCallback(e)
      return false
    }
    return true
  }

  close() {
    if (this._isOpen) {
      this.ws?.close()
      this.ws = undefined
    }
  }
}
