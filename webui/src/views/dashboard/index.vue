<script setup lang="ts">
import ClientStatus from './components/clientStatus.vue'
import StatisticInfo from './components/statisticInfo.vue'
import { useI18n } from 'vue-i18n'
import { useViewRoute } from '@/router'

const { t } = useI18n()
const [_r, _c, goto] = useViewRoute()
</script>

<template>
  <a-space direction="vertical" fill>
    <a-space style="display: flex; justify-content: space-between">
      <a-typography-text style="font-size: 1.2em">
        {{ t('page.dashboard.description') }}
      </a-typography-text>
      <a-button type="text" @click="goto('config')">{{
        t('page.dashboard.runningStatus')
      }}</a-button>
    </a-space>
    <StatisticInfo />
    <br />
    <ClientStatus />
  </a-space>
</template>
