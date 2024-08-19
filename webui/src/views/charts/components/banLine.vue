<template>
  <a-card hoverable :title="t('page.charts.title.line')">
    <template #extra>
      <a-popover>
        <a-link>{{ t('page.charts.options.more') }}</a-link>
        <template #content>
          <a-form :model="option">
            <a-form-item
              field="timeStep"
              :label="t('page.charts.options.steps')"
              label-col-flex="100px"
            >
              <a-radio-group
                v-model="option.timeStep"
                @change="(v: string | number | boolean) => changeStep(v as string)"
              >
                <a-radio value="day">{{ t('page.charts.options.day') }}</a-radio>
                <a-radio value="hour">{{ t('page.charts.options.hour') }}</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item
              field="range"
              :label="t('page.charts.options.days')"
              label-col-flex="100px"
            >
              <a-range-picker
                show-time
                v-model="option.range"
                value-format="Date"
                :shortcuts="
                  option.timeStep === 'day'
                    ? [
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
                      ]
                    : [
                        {
                          label: t('page.charts.options.shortcut.6hours'),
                          value: () => [
                            dayjs().startOf('hour').add(-6, 'hour').toDate(),
                            new Date()
                          ]
                        },
                        {
                          label: t('page.charts.options.shortcut.12hours'),
                          value: () => [
                            dayjs().startOf('hour').add(-12, 'hour').toDate(),
                            new Date()
                          ]
                        },
                        {
                          label: t('page.charts.options.shortcut.24hours'),
                          value: () => [
                            dayjs().startOf('hour').add(-24, 'hour').toDate(),
                            new Date()
                          ]
                        }
                      ]
                "
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
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ref, reactive, watch, computed } from 'vue'
import { useRequest } from 'vue-request'
import { SVGRenderer } from 'echarts/renderers'
import { useDarkStore } from '@/stores/dark'
import dayjs from 'dayjs'
import { useI18n } from 'vue-i18n'
import { getTimebasedStaticsData } from '@/service/charts'
const { t } = useI18n()
const loadingOptions = computed(() => ({
  text: t('page.charts.loading'),
  color: darkStore.isDark ? 'rgb(60, 126, 255)' : 'rgb(22, 93, 255)',
  textColor: darkStore.isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgb(29, 33, 41)',
  maskColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.4)'
}))
const darkStore = useDarkStore()
use([TooltipComponent, LineChart, GridComponent, SVGRenderer])
const changeStep = (v: string) => {
  if (v === 'day') {
    option.range = [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()]
  } else {
    option.range = [dayjs().startOf('hour').add(-6, 'hour').toDate(), new Date()]
  }
}

const option = reactive({
  timeStep: 'day' as 'day' | 'hour',
  range: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date()]
})

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
      name: t('page.charts.line.options.field')
    }
  ]
})

watch(option, (v) => {
  run(v.range[0], v.range[1], v.timeStep)
})

const { loading, run } = useRequest(getTimebasedStaticsData, {
  defaultParams: [dayjs().startOf('day').add(-7, 'day').toDate(), new Date(), 'day'],
  onSuccess: (data) => {
    if (data.data) {
      const map = new Map<number, number>()
      for (
        let cur = dayjs(option.range[0]);
        cur.isBefore(dayjs(option.range[1]));
        cur = cur.add(1, option.timeStep)
      ) {
        map.set(cur.valueOf(), 0)
      }
      data.data.forEach((it) => {
        map.set(dayjs(it.timestamp).startOf(option.timeStep).valueOf(), it.count)
      })
      chartOptions.value.series[0].data = Array.from(map)
        .sort(([k1], [k2]) => k1 - k2)
        .map(([key, value]) => [new Date(key), value])
    }
  }
})
</script>
