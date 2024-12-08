<template>
  <a-tabs :default-active-key="0" lazy-load animation type="rounded">
    <template #extra>
      <a-popover position="bottom">
        <a-button>
          <template #icon><icon-eye /></template>{{ t('page.charts.options') }}
        </a-button>
        <template #content>
          <a-space direction="vertical">
            <a-checkbox v-model="showCharts.banTrends">{{
              t('page.charts.title.line')
            }}</a-checkbox>
            <a-checkbox v-model="showCharts.fieldPie">{{
              t('page.charts.title.fieldPie')
            }}</a-checkbox>
            <a-checkbox v-model="showCharts.ispPie">{{ t('page.charts.title.geoip') }}</a-checkbox>
            <a-checkbox v-model="showCharts.traffic">{{
              t('page.charts.title.traffic')
            }}</a-checkbox>
            <a-checkbox v-model="showCharts.trends">{{ t('page.charts.title.trends') }}</a-checkbox>
          </a-space>
        </template>
      </a-popover>
    </template>
    <a-tab-pane v-for="(downloader, i) of tabList" :key="i" :title="downloader.title">
      <chartGrid :downloader="downloader.name" :show-charts="showCharts" />
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import { getDownloaders } from '@/service/downloaders'
import chartGrid from './grid.vue'
const { t } = useI18n()

import { useUserStore } from '@/stores/userStore'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const userStore = useUserStore()
const showCharts = ref(userStore.showCharts)
const { data: downloaderList } = useRequest(getDownloaders)
watch(showCharts, () => {
  userStore.setShowCharts(showCharts.value)
})
const tabList = computed(() => [
  {
    name: 'all',
    title: t('page.charts.all')
  },
  ...(downloaderList.value?.data.map((i) => ({
    name: i.name,
    title: i.name
  })) ?? [])
])
</script>

<style>
.chart {
  height: 440px;
}
.chart-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
</style>
