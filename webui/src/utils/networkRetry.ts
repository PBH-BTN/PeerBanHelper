import { Notification, Space, Button } from '@arco-design/web-vue'
import { h } from 'vue'
import i18n from '../locale'

function minDelay<T>(promise: Promise<T>, delay: number): Promise<T> {
  return Promise.all([promise, new Promise((resolve) => setTimeout(resolve, delay))] as const).then(
    ([result]) => result
  )
}

function networkFailRetryNotication<T>(
  retry: () => Promise<readonly [false, T] | [true, any]>,
  onCancel: () => Error
): Promise<T> {
  const NetWorkFailRetryNotificationId = 'network-error-retry' as const

  function newCounter(count: number, update: (count: number) => void) {
    const timmer = setInterval(() => {
      if (count <= 0) {
        update(0)
        clearInterval(timmer)
      } else {
        count -= 1
        update(count)
      }
    }, 1000)
    update(count)
    return timmer
  }

  let timmer: Timeout

  function renderNotication(props: {
    count: number
    loading?: boolean
    handleRetry: () => void
    handleCancel: () => void
  }) {
    const { t } = i18n.global
    Notification.warning({
      id: NetWorkFailRetryNotificationId,
      duration: 0,
      footer: () =>
        h(Space, null, () => [
          h(Button, { onClick: props.handleRetry, type: 'primary', loading: props.loading }, () =>
            t('service.networkErrorRetry.retry')
          ),
          h(Button, { onClick: props.handleCancel, disabled: props.loading }, () =>
            t('service.networkErrorRetry.cancel')
          )
        ]),
      content:
        props.count > 0
          ? t('service.networkErrorRetry', {
              time: t('service.networkErrorRetry.second', props.count)
            })
          : t('service.networkErrorRetry.loading')
    })
  }

  return new Promise<T>((resolve, reject) => {
    const handleCancel = () => {
      reject(onCancel())
      clearInterval(timmer)
      Notification.remove(NetWorkFailRetryNotificationId)
    }
    const handleRetry = (init = false) => {
      timmer && clearInterval(timmer)
      !init &&
        renderNotication({
          count: 0,
          loading: true,
          handleRetry,
          handleCancel
        })
      return minDelay(retry(), 1000)
        .then(([needRetry, res]) => {
          if (needRetry) newTimmer()
          else {
            resolve(res)
            Notification.remove(NetWorkFailRetryNotificationId)
          }
        })
        .catch((err) => {
          reject(err)
          Notification.remove(NetWorkFailRetryNotificationId)
        })
    }
    const newTimmer = () => {
      timmer = newCounter(15, (count) => {
        if (count === 0) handleRetry()
        else
          renderNotication({
            count,
            handleRetry,
            handleCancel
          })
      })
    }
    handleRetry(true)
  })
}

export default networkFailRetryNotication
