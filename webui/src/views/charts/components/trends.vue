<template>
  <a-card hoverable :title="t('page.charts.title.trends')">
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

<script lang="ts" setup>
import dayjs from 'dayjs'
import { useI18n } from 'vue-i18n'
import { getTrends } from '@/service/charts'
import { computed, reactive, ref, watch } from 'vue'
import { useRequest } from 'vue-request'
import { useDarkStore } from '@/stores/dark'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([GridComponent, TooltipComponent, LineChart, SVGRenderer])

const { t } = useI18n()

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

const chartOptions = ref({
  xAxis: {
    type: 'time',
    max: 'dataMax'
  },
  yAxis: {
    type: 'value'
  },
  tooltip: {
    trigger: 'axis'
  },
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
  run(v.range[0], v.range[1])
})

const { loading, run } = useRequest(getTrends, {
  defaultParams: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()],
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
  }
})
</script>
