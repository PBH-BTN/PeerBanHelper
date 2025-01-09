<template>
  <a-button
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
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const endpointStore = useEndpointStore()

const pausingStatus = computed(() => endpointStore.globalConfig?.globalPaused)

const handleGlobalPauseBtnClick = async () => {
  if (pausingStatus.value) {
    try {
      await endpointStore.updateGlobalConfig({ globalPaused: false })
      Message.warning(t('global.pause.pauseAll.stop'))
      return true
    } catch (e) {
      if (e instanceof Error) {
        Message.error(e.message)
      }
      return false
    }
  } else {
    Modal.warning({
      title: t('globalPauseModel.title'),
      content: t('globalPauseModel.description'),
      onBeforeOk: async (): Promise<boolean> => {
        try {
          await endpointStore.updateGlobalConfig({ globalPaused: true })
          Message.warning(t('global.pause.pauseAll.result'))
          unbanIP('*')
          return true
        } catch (e) {
          if (e instanceof Error) {
            Message.error(e.message)
          }
          return false
        }
      },
      hideCancel: false
    })
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
