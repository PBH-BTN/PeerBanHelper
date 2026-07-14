import urlJoin from 'url-join'

export class SSEHandler<T> {
  #isOpen: boolean = false

  isOpen(): boolean {
    return this.#isOpen
  }

  private es?: EventSource

  private url: URL

  constructor(endpoint: string, path: string, token: string) {
    const url = new URL(endpoint)
    this.url = new URL(urlJoin(`${url.protocol}//${url.host}${url.pathname}`, path))
    this.url.searchParams.append('token', token)
  }

  open(offset: number, callback: (data: T) => unknown, errorCallback: (e: Error) => void): boolean {
    try {
      this.es?.close() // 关闭旧链接
      this.url.searchParams.set('offset', offset.toString())
      this.es = new EventSource(this.url)
      this.es.onopen = () => {
        this.#isOpen = true
      }
      this.es.onerror = () => {
        errorCallback(new Error('SSE connection error'))
      }
      this.es.onmessage = (event) => {
        try {
          const data: T = JSON.parse(event.data)
          callback(data)
        } catch (_) {
          console.log('Drop invalid JSON:', event.data)
        }
      }
    } catch (e) {
      if (e instanceof Error) errorCallback(e)
      return false
    }
    return true
  }

  close() {
    if (this.#isOpen) {
      this.es?.close()
      this.es = undefined
    }
  }
}
