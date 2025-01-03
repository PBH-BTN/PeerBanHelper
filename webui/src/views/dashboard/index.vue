<template>
  <a-space direction="vertical" fill>
    <a-space style="display: flex; justify-content: space-between">
      <a-typography-text style="font-size: 1.2em">
        {{ t('page.dashboard.description') }}
      </a-typography-text>
      <a-space>
        <a-tooltip
          v-if="endpointStore.globalConfig?.globalPaused"
          :content="t('page.dashboard.pauseAll.result')"
        >
          <a-button type="dashed" disabled status="warning">
            <template #icon>
              <icon-pause />
            </template>
            {{ t('page.dashboard.pauseAll') }}
          </a-button>
        </a-tooltip>
        <a-popconfirm
          v-if="false"
          type="warning"
          :content="t('page.dashboard.pauseAll.tips')"
          :on-before-ok="pauseAll"
        >
          <a-button type="text" status="warning">
            <template #icon>
              <icon-pause />
            </template>
            {{ t('page.dashboard.pauseAll') }}
          </a-button>
        </a-popconfirm>
        <a-button type="text" @click="goto('config')">
          <template #icon>
            <IconFont type="icon-icon_status" />
          </template>
          {{ t('page.dashboard.runningStatus') }}
        </a-button>
      </a-space>
    </a-space>
    <StatisticInfo />
    <br />
    <ClientStatus />
  </a-space>
</template>
<script setup lang="ts">
import IconFont from '@/components/iconFont'
import { useViewRoute } from '@/router'
import { unbanIP } from '@/service/banList'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import ClientStatus from './components/clientStatus.vue'
import StatisticInfo from './components/statisticInfo.vue'

const endpointStore = useEndpointStore()
const { t } = useI18n()
const [_r, _c, goto] = useViewRoute()

const pauseAll = async (): Promise<boolean> => {
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
</script>
