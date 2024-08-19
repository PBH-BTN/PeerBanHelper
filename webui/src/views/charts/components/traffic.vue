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
                show-time
                v-model="option.range"
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
    <v-chart
      class="chart"
      :option="chartOptions"
      :loading="loading"
      :loadingOptions="loadingOptions"
      theme="ovilia-green"
      autoresize
      :init-options="{ renderer: 'svg' }"
    />
  </a-card>
</template>
<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, ToolboxComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { useDarkStore } from '@/stores/dark'
import { getTraffic } from '@/service/charts'
import { useRequest } from 'vue-request'
import VChart from 'vue-echarts'
import { formatFileSize } from '@/utils/file'
import type { CallbackDataParams } from 'echarts/types/dist/shared'

use([TooltipComponent, LegendComponent, ToolboxComponent, GridComponent, BarChart, SVGRenderer])

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

const chartOptions = ref({
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'shadow'
    },
    formatter: function (value: CallbackDataParams[]) {
      return (
        value[0]?.name +
        ':<br/>' +
        value
          .map((params: CallbackDataParams) => {
            return `${params.marker} ${params.seriesName}: ${formatFileSize(params.value as number)}`
          })
          .join('<br>')
      )
    }
  },
  legend: {
    data: [t('page.charts.traffic.options.download'), t('page.charts.traffic.options.upload')]
  },

  xAxis: [
    {
      type: 'category',
      axisTick: { show: false },
      data: [] as string[]
    }
  ],
  yAxis: [
    {
      type: 'value',
      axisLabel: {
        formatter: (value: number) => {
          return formatFileSize(value)
        }
      }
    }
  ],
  series: [
    {
      name: t('page.charts.traffic.options.download'),
      type: 'bar',
      barGap: 0,
      emphasis: {
        focus: 'series'
      },
      data: [] as number[]
    },
    {
      name: t('page.charts.traffic.options.upload'),
      type: 'bar',
      emphasis: {
        focus: 'series'
      },
      data: [] as number[]
    }
  ]
})

watch(option, (v) => {
  run(v.range[0], v.range[1])
})

const { loading, run } = useRequest(getTraffic, {
  defaultParams: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()],
  onSuccess: (data) => {
    if (data.data) {
      chartOptions.value.xAxis[0].data.splice(0)
      chartOptions.value.series[0].data.splice(0)
      chartOptions.value.series[1].data.splice(0)
      data.data.journal.forEach((record) => {
        chartOptions.value.xAxis[0].data.push(d(new Date(record.timestamp), 'short'))
        chartOptions.value.series[0].data.push(record.downloaded)
        chartOptions.value.series[1].data.push(record.uploaded)
      })
    }
  }
})
</script>
