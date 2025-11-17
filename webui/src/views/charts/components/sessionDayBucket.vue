<template>
  <a-card hoverable :title="t('page.charts.title.sessionDayBucket')">
    <template #extra>
      <a-form :model="option" auto-label-width>
        <a-form-item field="range" style="margin-bottom: 0">
          <template #label>
            {{ t('page.charts.options.days') }}
            <a-tooltip :content="t('page.charts.tooltip.sessionDayBucket')">
              <icon-question-circle />
            </a-tooltip>
          </template>
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
    <a-empty
      v-else-if="!loading && (!data?.data || data.data.length === 0)"
      class="chart"
      style="align-items: center; display: flex; justify-content: center; flex-direction: column"
    />
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
<script setup lang="ts">
import { getSessionDayBucket } from '@/service/charts'
import { useDarkStore } from '@/stores/dark'
import dayjs from 'dayjs'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  ToolboxComponent,
  TooltipComponent
} from 'echarts/components'
import { use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import type { CallbackDataParams } from 'echarts/types/dist/shared'
import type { OptionDataValue } from 'echarts/types/src/util/types.js'
import { computed, reactive, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

use([TooltipComponent, LegendComponent, ToolboxComponent, GridComponent, LineChart, SVGRenderer])

const option = reactive({
  range: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()]
})
const darkStore = useDarkStore()
const loadingOptions = computed(() => ({
  text: t('page.charts.loading'),
  color: darkStore.isDark ? 'rgb(60, 126, 255)' : 'rgb(22, 93, 255)',
  textColor: darkStore.isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgb(29, 33, 41)',
  maskColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.4)'
}))

const { t, d } = useI18n()

const err = ref<Error>()
const usedOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'shadow'
    },
    backgroundColor: darkStore.isDark ? '#333335' : '#ffffff',
    borderColor: darkStore.isDark ? '#333335' : '#ffffff',
    textStyle: {
      color: darkStore.isDark ? 'rgba(255, 255, 255, 0.7)' : undefined
    },
    formatter: function (value: CallbackDataParams[]) {
      return (
        d((value[0]!.data as OptionDataValue[])[0] as Date, 'short') +
        ':<br/>' +
        value
          .map((params: CallbackDataParams) => {
            return `${params.marker} ${params.seriesName}: ${(params.data as OptionDataValue[])[1]}`
          })
          .join('<br>')
      )
    }
  },
  backgroundColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.0)' : undefined,
  ...chartOptions.value
}))

const chartOptions = ref({
  legend: {
    data: [
      t('page.charts.sessionDayBucket.options.total'),
      t('page.charts.sessionDayBucket.options.incoming'),
      t('page.charts.sessionDayBucket.options.remoteRefuseTransferToClient'),
      t('page.charts.sessionDayBucket.options.remoteAcceptTransferToClient'),
      t('page.charts.sessionDayBucket.options.localRefuseTransferToPeer'),
      t('page.charts.sessionDayBucket.options.localAcceptTransferToPeer'),
      t('page.charts.sessionDayBucket.options.localNotInterested'),
      t('page.charts.sessionDayBucket.options.questionStatus'),
      t('page.charts.sessionDayBucket.options.optimisticUnchoke'),
      t('page.charts.sessionDayBucket.options.fromDHT'),
      t('page.charts.sessionDayBucket.options.fromPEX'),
      t('page.charts.sessionDayBucket.options.fromLSD'),
      t('page.charts.sessionDayBucket.options.fromTrackerOrOther'),
      t('page.charts.sessionDayBucket.options.rc4Encrypted'),
      t('page.charts.sessionDayBucket.options.plainTextEncrypted'),
      t('page.charts.sessionDayBucket.options.utpSocket'),
      t('page.charts.sessionDayBucket.options.tcpSocket')
    ]
  },

  xAxis: {
    type: 'time',
    max: 'dataMax',
    min: 'dataMin',
    minInterval: 3600 * 24 * 1000
  },
  grid: {
    left: '15%'
  },
  yAxis: {
    type: 'value'
  },
  series: [
    {
      name: t('page.charts.sessionDayBucket.options.total'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.incoming'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.remoteRefuseTransferToClient'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.remoteAcceptTransferToClient'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.localRefuseTransferToPeer'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.localAcceptTransferToPeer'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.localNotInterested'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.questionStatus'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.optimisticUnchoke'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.fromDHT'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.fromPEX'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.fromLSD'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.fromTrackerOrOther'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.rc4Encrypted'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.plainTextEncrypted'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.utpSocket'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.sessionDayBucket.options.tcpSocket'),
      type: 'line',
      stack: 'Total',
      areaStyle: {},
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    }
  ]
})

const props = defineProps<{
  downloader?: string
}>()

watch(option, (v) => {
  run(v.range[0]!, v.range[1]!, props.downloader)
})

const { loading, run, refresh, data } = useRequest(getSessionDayBucket, {
  defaultParams: [option.range[0]!, option.range[1]!, props.downloader],
  onSuccess: (data) => {
    if (data.data) {
      chartOptions.value.series![0]!.data = data.data.map((v) => [new Date(v.key), v.totalConnections])
      chartOptions.value.series![1]!.data = data.data.map((v) => [new Date(v.key), v.incomingConnections])
      chartOptions.value.series![2]!.data = data.data.map((v) => [new Date(v.key), v.remoteRefuseTransferToClient])
      chartOptions.value.series![3]!.data = data.data.map((v) => [new Date(v.key), v.remoteAcceptTransferToClient])
      chartOptions.value.series![4]!.data = data.data.map((v) => [new Date(v.key), v.localRefuseTransferToPeer])
      chartOptions.value.series![5]!.data = data.data.map((v) => [new Date(v.key), v.localAcceptTransferToPeer])
      chartOptions.value.series![6]!.data = data.data.map((v) => [new Date(v.key), v.localNotInterested])
      chartOptions.value.series![7]!.data = data.data.map((v) => [new Date(v.key), v.questionStatus])
      chartOptions.value.series![8]!.data = data.data.map((v) => [new Date(v.key), v.optimisticUnchoke])
      chartOptions.value.series![9]!.data = data.data.map((v) => [new Date(v.key), v.fromDHT])
      chartOptions.value.series![10]!.data = data.data.map((v) => [new Date(v.key), v.fromPEX])
      chartOptions.value.series![11]!.data = data.data.map((v) => [new Date(v.key), v.fromLSD])
      chartOptions.value.series![12]!.data = data.data.map((v) => [new Date(v.key), v.fromTrackerOrOther])
      chartOptions.value.series![13]!.data = data.data.map((v) => [new Date(v.key), v.rc4Encrypted])
      chartOptions.value.series![14]!.data = data.data.map((v) => [new Date(v.key), v.plainTextEncrypted])
      chartOptions.value.series![15]!.data = data.data.map((v) => [new Date(v.key), v.utpSocket])
      chartOptions.value.series![16]!.data = data.data.map((v) => [new Date(v.key), v.tcpSocket])
    }
  },
  onError: (e) => {
    err.value = e
  }
})
</script>
