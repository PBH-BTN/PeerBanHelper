import { useStorage } from '@vueuse/core'
import { defineStore } from 'pinia'
import { onUnmounted, readonly, ref, watch } from 'vue'
import { type PluginImplementType } from 'vue-request'
type MapValue<T> = T extends Map<unknown, infer V> ? V : never
type status = 'idle' | 'loading'
class AutoUpdateMessageChannel extends MessageChannel {
  private count = 0
  private refreshMap: Map<
    number,
    {
      status: status
      refresh: () => void
    }
  > = new Map()

  constructor(onChange: (status: status) => void) {
    super()
    let lastStatus: status = 'idle'
    this.port1.onmessage = () => {
      const status = this.getStatus()
      if (status !== lastStatus) {
        lastStatus = status
        onChange(status)
      }
    }
  }

  getStatus() {
    for (const item of this.refreshMap.values()) {
      if (item.status === 'loading') {
        return 'loading'
      }
    }
    return 'idle'
  }

  doRefresh() {
    this.refreshMap.forEach((item) => {
      item.refresh()
    })
  }

  polling(refresh: () => void) {
    const currentId = this.count++
    const item: MapValue<typeof this.refreshMap> = {
      status: 'idle',
      refresh
    }
    this.refreshMap.set(currentId, item)
    return (action: 'loading' | 'idle' | 'unmount') => {
      if (action === 'unmount') {
        this.refreshMap.delete(currentId)
      } else {
        item.status = action
      }
      this.port2.postMessage(null)
    }
  }
}
const isServer = typeof window === 'undefined'
const isNil = (val: unknown) => val === null || val === undefined
const isDocumentVisibility = () => {
  if (isServer || isNil(window.document?.visibilityState)) return true
  return window.document.visibilityState === 'visible'
}
const isOnline = () => (!isServer && window.navigator?.onLine) ?? true

export const useAutoUpdate = defineStore('autoUpdate', () => {
  const lastUpdate = ref(new Date())
  const autoUpdate = useStorage('autoUpdate.enable', true)
  const interval = useStorage('autoUpdate.interval', 3000)
  const status = ref<status>('idle')

  const stopPollingWhenHiddenOrOffline = ref(false)
  const isKeepPolling = () => isDocumentVisibility() && isOnline()
  const pollingTimer = ref<ReturnType<typeof polling>>()
  const polling = (pollingFunc: () => void) => {
    let timerId: Timeout | undefined
    if (autoUpdate.value) {
      if (isKeepPolling()) {
        timerId = setTimeout(pollingFunc, interval.value)
      } else {
        // stop polling
        stopPollingWhenHiddenOrOffline.value = true
        return
      }
    }

    return () => timerId && clearTimeout(timerId)
  }
  const channel = new AutoUpdateMessageChannel((value) => {
    status.value = value
    if (value === 'idle') {
      lastUpdate.value = new Date()
      pollingTimer.value = polling(() => channel.doRefresh())
    } else {
      pollingTimer.value?.()
    }
  })

  const rePolling = () => {
    if (stopPollingWhenHiddenOrOffline.value && isKeepPolling()) {
      channel.doRefresh()
      stopPollingWhenHiddenOrOffline.value = false
    }
  }

  watch(autoUpdate, () => {
    if (pollingTimer.value) {
      pollingTimer.value()
    }
    if (autoUpdate.value) {
      pollingTimer.value = polling(() => channel.doRefresh())
    }
  })

  window.addEventListener('visibilitychange', () => isDocumentVisibility() && rePolling(), false)
  window.addEventListener('online', rePolling, false)

  return {
    lastUpdate,
    autoUpdate,
    interval,
    status: readonly(status),
    polling: channel.polling.bind(channel),
    refresh: () => {
      pollingTimer.value?.()
      channel.doRefresh()
    }
  }
})

export const useAutoUpdatePlugin = <R, P extends unknown[]>(
  queryInstance: Parameters<PluginImplementType<R, P>>[0]
): ReturnType<PluginImplementType<R, P>> => {
  const autoupdateStore = useAutoUpdate()
  const callbackRef = ref<ReturnType<AutoUpdateMessageChannel['polling']>>()

  callbackRef.value = autoupdateStore.polling(() => queryInstance.context.refresh())

  onUnmounted(() => {
    callbackRef.value?.('unmount')
  })

  return {
    onBefore() {
      callbackRef.value?.('loading')
    },
    onCancel() {
      callbackRef.value?.('idle')
    },
    onAfter() {
      callbackRef.value?.('idle')
    }
  }
}

export const useFirstPageOnlyAutoUpdatePlugin = <R, P extends unknown[]>(
  queryInstance: Parameters<PluginImplementType<R, P>>[0]
): ReturnType<PluginImplementType<R, P>> => {
  const autoupdateStore = useAutoUpdate()
  const callbackRef = ref<ReturnType<AutoUpdateMessageChannel['polling']>>()

  callbackRef.value = autoupdateStore.polling(() => {
    // Only refresh when on the first page
    const params = queryInstance.params.value?.[0] as { page?: number } | undefined
    if ((params?.page ?? 1) === 1) {
      queryInstance.context.refresh()
    }
  })

  onUnmounted(() => {
    callbackRef.value?.('unmount')
  })

  return {
    onBefore() {
      callbackRef.value?.('loading')
    },
    onCancel() {
      callbackRef.value?.('idle')
    },
    onAfter() {
      callbackRef.value?.('idle')
    }
  }
}
