<template>
  <a-space direction="vertical" fill>
    <a-space style="display: flex; justify-content: space-between">
      <a-typography-text style="font-size: 1.2em">
        {{ t('page.dashboard.description') }}
      </a-typography-text>
      <a-button type="text" @click="goto('config')">
        <template #icon>
          <IconFont type="icon-icon_status" />
        </template>
        {{ t('page.dashboard.runningStatus') }}
      </a-button>
    </a-space>
    <StatisticInfo />
    <br />
    <ClientStatus />
  </a-space>
</template>
<script setup lang="ts">
import IconFont from '@/components/iconFont'
import { useViewRoute } from '@/router'
import { useI18n } from 'vue-i18n'
import ClientStatus from './components/clientStatus.vue'
import StatisticInfo from './components/statisticInfo.vue'

const { t } = useI18n()
const [_r, _c, goto] = useViewRoute()
</script>
