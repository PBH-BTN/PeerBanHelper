<template>
  <a-card hoverable :title="t('page.charts.title.line')">
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
import { getBanTrends } from '@/service/charts'
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
const { t } = useI18n()
const loadingOptions = computed(() => ({
  text: t('page.charts.loading'),
  color: darkStore.isDark ? 'rgb(60, 126, 255)' : 'rgb(22, 93, 255)',
  textColor: darkStore.isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgb(29, 33, 41)',
  maskColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.4)'
}))

const darkStore = useDarkStore()
const err = ref<Error>()

use([TooltipComponent, LineChart, GridComponent, SVGRenderer])
const props = defineProps<{
  downloader?: string
}>()

const option = reactive({
  range: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()]
})

const usedOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: darkStore.isDark ? '#333335' : '#ffffff',
    borderColor: darkStore.isDark ? '#333335' : '#ffffff',
    textStyle: {
      color: darkStore.isDark ? 'rgba(255, 255, 255, 0.7)' : undefined
    }
  },
  backgroundColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.0)' : undefined,
  ...chartOptions.value
}))

const chartOptions = ref({
  xAxis: {
    type: 'time',
    max: 'dataMax'
  },
  yAxis: {
    type: 'value'
  },
  grid: {
    left: '15%'
  },
  series: [
    {
      data: [] as [Date, number][],
      type: 'line',
      name: t('page.charts.line.options.field')
    }
  ]
})

watch(option, (v) => {
  run(v.range[0], v.range[1], props.downloader)
})

const { loading, run, refresh } = useRequest(getBanTrends, {
  defaultParams: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date(), props.downloader],
  onSuccess: (data) => {
    if (data.data) {
      chartOptions.value.series[0].data = data.data
        .sort((kv1, kv2) => kv1.key - kv2.key)
        .map((kv) => [new Date(kv.key), kv.value])
    }
  },
  onError: (e) => {
    err.value = e
  }
})
</script>
