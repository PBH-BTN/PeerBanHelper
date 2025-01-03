<template>
  <a-button
    ref="globalPauseBtn"
    class="global-pause-btn"
    :type="pausingStatus ? 'primary' : 'outline'"
    :status="pausingStatus ? 'warning' : 'normal'"
    :shape="'circle'"
    @click="handleGlobalPauseBtnClick"
  >
    <icon-pause
      id="spin"
      :class="{
        loading: false
      }"
    />
  </a-button>
</template>

<script setup lang="ts">
import { unbanIP } from '@/service/banList'
import { useEndpointStore } from '@/stores/endpoint'
import { Message, Modal } from '@arco-design/web-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const endpointStore = useEndpointStore()
const globalPauseBtn = ref()
// const loadingHolding = ref(false)
// onMounted(() => {
//   container.value = window.document.querySelector('body') as HTMLElement
//   eventAbortController = new AbortController()
//   globalPauseBtn.value.$el.addEventListener(
//     'animationstart',
//     () => {
//       loadingHolding.value = true
//     },
//     { signal: eventAbortController.signal }
//   )
//   globalPauseBtn.value.$el.addEventListener(
//     'animationend',
//     () => {
//       loadingHolding.value = false
//     },
//     { signal: eventAbortController.signal }
//   )
// })

// onUnmounted(() => {
//   eventAbortController.abort()
// })

const pausingStatus = computed(() => endpointStore.globalConfig?.globalPaused)

const globalPause = async (): Promise<boolean> => {
  try {
    await endpointStore.updateGlobalConfig({ globalPaused: true })
    Message.warning(t('page.dashboard.pauseAll.result'))
    unbanIP('*')
    return true
  } catch (e) {
    if (e instanceof Error) {
      Message.error(e.message)
    }
    return false
  }
}

const handleGlobalPauseBtnClick = () => {
  if (pausingStatus.value) {
    cancelGlobalPause()
  } else {
    Modal.warning({
      title: t('globalPauseModel.title'),
      content: t('globalPauseModel.description'),
      onBeforeOk: () => globalPause(),
      hideCancel: false
    })
  }
}

const cancelGlobalPause = async (): Promise<boolean> => {
  try {
    await endpointStore.updateGlobalConfig({ globalPaused: false })
    Message.warning(t('page.dashboard.pauseAll.stop'))
    unbanIP('*')
    return true
  } catch (e) {
    if (e instanceof Error) {
      Message.error(e.message)
    }
    return false
  }
}
</script>

<style lang="less" scoped>
.global-pause-btn:not(.arco-btn-primary) {
  border-color: rgb(var(--gray-2));
  color: rgb(var(--gray-8));
}

.global-pause-btn,
.global-pause-btn:hover {
  font-size: 16px;

  #spin {
    &.loading {
      animation: whirl 0.25s linear infinite;

      &.loading-holding {
        animation-iteration-count: 1;
      }
    }
  }
}

@keyframes whirl {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(180deg);
  }
}
</style>
