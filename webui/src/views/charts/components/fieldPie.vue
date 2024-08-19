<template>
  <a-card hoverable :title="t('page.charts.options.field.' + option.field)">
    <v-chart
      class="chart"
      :option="chartOption"
      :loading="loading"
      autoresize
      :loadingOptions="loadingOptions"
      :theme="darkStore.isDark ? 'dark' : 'light'"
      :init-options="{ renderer: 'svg' }"
    >
    </v-chart>
    <template #extra>
      <a-popover>
        <a-link>{{ t('page.charts.options.more') }}</a-link>
        <template #content>
          <a-form :model="option" style="width: 25vh">
            <a-form-item field="field" :label="t('page.charts.options.field')">
              <a-select v-model="option.field" :trigger-props="{ autoFitPopupMinWidth: true }">
                <a-option value="peerId">
                  {{ t('page.charts.options.field.peerId') }}
                </a-option>
                <a-option value="torrentName">
                  {{ t('page.charts.options.field.torrentName') }}
                </a-option>
                <a-option value="module">
                  {{ t('page.charts.options.field.module') }}
                </a-option>
              </a-select>
            </a-form-item>
            <a-form-item field="enableThreshold">
              <a-space>
                <a-switch v-model="option.enableThreshold" />
                <a-typography-text>{{ t('page.charts.options.thresold') }}</a-typography-text>
              </a-space>
            </a-form-item>
            <a-form-item field="mergeSameVersion" v-if="option.field === 'peerId'">
              <a-space>
                <a-switch v-model="option.mergeSameVersion" />
                <a-typography-text>{{ t('page.charts.options.mergeSame') }}</a-typography-text>
              </a-space>
            </a-form-item>
          </a-form>
        </template>
      </a-popover>
    </template>
  </a-card>
</template>
<script lang="ts" setup>
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import { ref, reactive, watch, computed } from 'vue'
import { getAnalysisDataByField } from '@/service/charts'
import { useRequest } from 'vue-request'
import VChart from 'vue-echarts'
import { useDarkStore } from '@/stores/dark'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

use([TooltipComponent, LegendComponent, PieChart, SVGRenderer])
const darkStore = useDarkStore()

const option = reactive({
  field: 'peerId' as 'peerId' | 'torrentName' | 'module',
  enableThreshold: true,
  mergeSameVersion: false
})
const loadingOptions = computed(() => ({
  text: t('page.charts.loading'),
  color: darkStore.isDark ? 'rgb(60, 126, 255)' : 'rgb(22, 93, 255)',
  textColor: darkStore.isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgb(29, 33, 41)',
  maskColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.4)'
}))

const chartOption = ref({
  tooltip: {
    trigger: 'item',
    appendToBody: true,
    formatter: '<p style="word-wrap:break-all"><b>{b}</b></p>  {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    left: 'right',
    type: 'scroll',
    right: 10,
    top: 20,
    bottom: 20,
    data: [] as string[],
    textStyle: {
      overflow: 'truncate',
      width: 100
    },
    tooltip: {
      show: true
    }
  },
  backgroundColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.0)' : undefined,
  series: [
    {
      name: t('page.charts.options.field.' + option.field),
      type: 'pie',
      radius: '55%',
      center: ['50%', '60%'],
      data: [] as { name: string; value: number }[],
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }
  ]
})

watch(option, (v) => {
  run(v.field, v.enableThreshold)
})

const { loading, run } = useRequest(getAnalysisDataByField, {
  defaultParams: ['peerId', true],
  onSuccess: (data) => {
    if (data.data) {
      const nonEmptyData = data.data.map((it) => {
        if (it.data === '') it.data = t('page.charts.options.field.empty')
        return it
      })
      if (option.mergeSameVersion && option.field === 'peerId') {
        const map = new Map<string, number>()
        nonEmptyData.forEach((it) => {
          let key = it.data
          const match = key.match(/^([-]?[a-zA-z]+)[0-9]+.*/)
          if (match && match?.length >= 2) key = match[1] + '*'
          if (map.has(key)) {
            map.set(key, map.get(key)!! + it.count)
          } else {
            map.set(key, it.count)
          }
        })
        chartOption.value.legend.data = []
        chartOption.value.series[0].data = []
        Array.from(map).forEach(([key, value]) => {
          chartOption.value.legend.data.push(key)
          chartOption.value.series[0].data.push({
            name: key,
            value
          })
        })
      } else {
        chartOption.value.legend.data = nonEmptyData.map((it) => it.data)
        chartOption.value.series[0].data = nonEmptyData.map((it) => ({
          name: it.data,
          value: it.count
        }))
      }
    }
  }
})
</script>
