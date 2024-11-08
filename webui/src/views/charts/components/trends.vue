<template>
  <a-card hoverable :title="t('page.charts.title.trends')">
    <template #extra>
      <a-form :model="option" auto-label-width>
        <a-form-item field="range" :label="t('page.charts.options.days')" style="margin-bottom: 0">
          <a-range-picker
            v-model="option.range"
            value-format="Date"
            style="width: 275px"
            :shortcuts="[
              {
                label: t('page.charts.options.shortcut.7days'),
                value: () => [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()]
              },
              {
                label: t('page.charts.options.shortcut.14days'),
                value: () => [dayjs().startOf('day').add(-14, 'day').toDate(), new Date()]
              },
              {
                label: t('page.charts.options.shortcut.30days'),
                value: () => [dayjs().startOf('day').add(-30, 'day').toDate(), new Date()]
              }
            ]"
          />
        </a-form-item>
      </a-form>
    </template>
    <a-result
      v-if="err"
      status="500"
      :title="t('page.charts.error.title')"
      class="chart chart-error"
    >
      <template #subtitle> {{ err.message }} </template>
      <template #extra>
        <a-button
          type="primary"
          @click="
            () => {
              err = undefined
              refresh()
            }
          "
          >{{ t('page.charts.error.refresh') }}</a-button
        >
      </template>
    </a-result>
    <v-chart
      v-else
      class="chart"
      :option="usedOption"
      :loading="loading"
      :loading-options="loadingOptions"
      :theme="darkStore.isDark ? 'dark' : 'ovilia-green'"
      autoresize
      :init-options="{ renderer: 'svg' }"
    />
  </a-card>
</template>

<script lang="ts" setup>
import { getTrends } from '@/service/charts'
import { useDarkStore } from '@/stores/dark'
import dayjs from 'dayjs'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { computed, reactive, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

use([GridComponent, TooltipComponent, LineChart, SVGRenderer])

const { t } = useI18n()

const option = reactive({
  range: [dayjs().startOf('day').add(-14, 'day').toDate(), new Date()]
})

const err = ref<Error>()

const darkStore = useDarkStore()
const loadingOptions = computed(() => ({
  text: t('page.charts.loading'),
  color: darkStore.isDark ? 'rgb(60, 126, 255)' : 'rgb(22, 93, 255)',
  textColor: darkStore.isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgb(29, 33, 41)',
  maskColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.4)'
}))
const usedOption = computed(() => chartOptions.value)

const chartOptions = ref({
  xAxis: {
    type: 'time',
    max: 'dataMax'
  },
  yAxis: {
    type: 'value'
  },
  tooltip: {
    trigger: 'axis',
    backgroundColor: darkStore.isDark ? '#333335' : '',
    borderColor: darkStore.isDark ? '#333335' : '',
    textStyle: {
      color: darkStore.isDark ? 'rgba(255, 255, 255, 0.7)' : ''
    }
  },
  backgroundColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.0)' : undefined,
  series: [
    {
      data: [] as [Date, number][],
      type: 'line',
      color: '#A5A051',
      areaStyle: {
        color: '#A5A051'
      },
      name: t('page.charts.trends.options.peers')
    },
    {
      data: [] as [Date, number][],
      type: 'line',
      color: '#DB4D6D',
      areaStyle: {
        color: '#DB4D6D'
      },
      name: t('page.charts.trends.options.bans')
    }
  ]
})

watch(option, (v) => {
  run(v.range[0], v.range[1], props.downloader)
})
const props = defineProps<{
  downloader?: string
}>()
const { loading, run, refresh } = useRequest(getTrends, {
  defaultParams: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date(), props.downloader],
  onSuccess: (data) => {
    if (data.data) {
      chartOptions.value.series[0].data = data.data.connectedPeersTrend
        .sort((a, b) => a.key - b.key)
        .map((it) => {
          return [new Date(it.key), it.value]
        })
      chartOptions.value.series[1].data = data.data.bannedPeersTrend
        .sort((a, b) => a.key - b.key)
        .map((it) => {
          return [new Date(it.key), it.value]
        })
    }
  },
  onError: (e) => {
    err.value = e
  }
})
</script>
