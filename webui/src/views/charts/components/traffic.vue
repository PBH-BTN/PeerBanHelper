<template>
  <a-card hoverable :title="t('page.charts.title.traffic')">
    <template #extra>
      <a-popover>
        <a-link>{{ t('page.charts.options.more') }}</a-link>
        <template #content>
          <a-form :model="option">
            <a-form-item
              field="range"
              :label="t('page.charts.options.days')"
              label-col-flex="100px"
            >
              <a-range-picker
                v-model="option.range"
                show-time
                value-format="Date"
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
        <a-tooltip :content="t('page.charts.tooltip.traffic')">
          <icon-question-circle />
        </a-tooltip>
      </a-popover>
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
      v-else-if="!data?.data && !loading"
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
import { getTraffic } from '@/service/charts'
import { useDarkStore } from '@/stores/dark'
import { formatFileSize } from '@/utils/file'
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
  range: [dayjs().startOf('day').add(-14, 'day').toDate(), new Date()]
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
const usedOption = computed(() => chartOptions.value)

const chartOptions = ref({
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'shadow'
    },
    backgroundColor: darkStore.isDark ? '#333335' : '',
    borderColor: darkStore.isDark ? '#333335' : '',
    textStyle: {
      color: darkStore.isDark ? 'rgba(255, 255, 255, 0.7)' : ''
    },
    formatter: function (value: CallbackDataParams[]) {
      return (
        d((value[0].data as OptionDataValue[])[0] as Date, 'short') +
        ':<br/>' +
        value
          .map((params: CallbackDataParams) => {
            return `${params.marker} ${params.seriesName}: ${formatFileSize((params.data as OptionDataValue[])[1] as number)}`
          })
          .join('<br>')
      )
    }
  },
  backgroundColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.0)' : undefined,
  legend: {
    data: [t('page.charts.traffic.options.download'), t('page.charts.traffic.options.upload')]
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
    type: 'value',
    axisLabel: {
      formatter: (value: number) => {
        return formatFileSize(value)
      }
    }
  },
  series: [
    {
      name: t('page.charts.traffic.options.download'),
      type: 'line',
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    },
    {
      name: t('page.charts.traffic.options.upload'),
      type: 'line',
      emphasis: {
        focus: 'series'
      },
      data: [] as [Date, number][]
    }
  ]
})

watch(option, (v) => {
  run(v.range[0], v.range[1], props.downloader)
})
const props = defineProps<{
  downloader?: string
}>()

const { loading, run, refresh, data } = useRequest(getTraffic, {
  defaultParams: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date(), props.downloader],
  onSuccess: (data) => {
    if (data.data) {
      chartOptions.value.series[0].data = data.data.map((v) => [
        new Date(v.timestamp),
        v.dataOverallDownloaded
      ])
      chartOptions.value.series[1].data = data.data.map((v) => [
        new Date(v.timestamp),
        v.dataOverallUploaded
      ])
    }
  },
  onError: (e) => {
    err.value = e
  }
})
</script>
