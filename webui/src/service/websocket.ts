import type { CommonResponse } from '@/api/model/common'
import urlJoin from 'url-join'

export class WebSocketHandler<T> {
  private _isOpen: boolean = false

  get isOpen(): boolean {
    return this._isOpen
  }

  get State() {
    return this.ws?.readyState ?? WebSocket.CLOSED
  }

  private ws?: WebSocket

  private url: URL

  constructor(endpoint: string, path: string, token: string) {
    const url = new URL(endpoint)
    const protocal = url.protocol === 'https:' ? 'wss' : 'ws'
    this.url = new URL(urlJoin(`${protocal}://${url.host}${url.pathname}`, path))
    this.url.searchParams.append('token', token)
  }

  open(offset: number, callback: (data: T) => unknown, errroCallback: (e: Error) => void): boolean {
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
          const res: CommonResponse<T> = JSON.parse(event.data)
          if (res.success) {
            callback(res.data)
          } else {
            errroCallback(new Error(`WebSocket error: ${res.message}`))
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
