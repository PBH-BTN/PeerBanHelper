<template>
  <a-card
    hoverable
    :title="
      t('page.charts.title.geoip') + (option.bannedOnly ? t('page.charts.subtitle.bannedOnly') : '')
    "
  >
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
      autoresize
      :loading-options="loadingOptions"
      :theme="darkStore.isDark ? 'dark' : 'light'"
      :init-options="{ renderer: 'svg' }"
    >
    </v-chart>
    <template #extra>
      <a-popover>
        <a-link>{{ t('page.charts.options.more') }}</a-link>
        <template #content>
          <a-form :model="option">
            <a-form-item field="field" :label="t('page.charts.options.field')">
              <a-select v-model="option.field" :trigger-props="{ autoFitPopupMinWidth: true }">
                <a-option value="isp">
                  {{ t('page.charts.options.field.isp') }}
                </a-option>
                <a-option value="province">
                  {{ t('page.charts.options.field.province') }}
                </a-option>
                <a-option value="city">
                  {{ t('page.charts.options.field.city') }}
                </a-option>
                <a-option value="region">
                  {{ t('page.charts.options.field.region') }}
                </a-option>
              </a-select>
            </a-form-item>
            <a-form-item field="range" :label="t('page.charts.options.days')">
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
            <a-form-item field="enableThreshold">
              <a-space>
                <a-switch v-model="option.enableThreshold" />
                <a-typography-text>{{ t('page.charts.options.thresold') }}</a-typography-text>
              </a-space>
            </a-form-item>
            <a-form-item field="bannedOnly">
              <a-space>
                <a-switch v-model="option.bannedOnly" />
                <a-typography-text>{{ t('page.charts.options.bannedOnly') }}</a-typography-text>
              </a-space>
            </a-form-item>
          </a-form>
        </template>
      </a-popover>
    </template>
  </a-card>
</template>

<script lang="ts" setup>
import { getGeoIPData } from '@/service/charts'
import { useDarkStore } from '@/stores/dark'
import dayjs from 'dayjs'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { computed, reactive, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()

use([TooltipComponent, LegendComponent, PieChart, SVGRenderer])
const darkStore = useDarkStore()
const err = ref<Error>()

const option = reactive({
  field: 'isp' as 'isp' | 'province' | 'city' | 'region',
  enableThreshold: true,
  bannedOnly: true,
  range: [dayjs().startOf('day').add(-14, 'day').toDate(), new Date()]
})

const loadingOptions = computed(() => ({
  text: t('page.charts.loading'),
  color: darkStore.isDark ? 'rgb(60, 126, 255)' : 'rgb(22, 93, 255)',
  textColor: darkStore.isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgb(29, 33, 41)',
  maskColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.4)' : 'rgba(255, 255, 255, 0.4)'
}))

const usedOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    appendToBody: true,
    formatter: '<p style="word-wrap:break-all"><b>{b}</b></p>  {c} ({d}%)',
    backgroundColor: darkStore.isDark ? '#333335' : '#ffffff',
    borderColor: darkStore.isDark ? '#333335' : '#ffffff',
    textStyle: {
      color: darkStore.isDark ? 'rgba(255, 255, 255, 0.7)' : undefined
    }
  },
  backgroundColor: darkStore.isDark ? 'rgba(0, 0, 0, 0.0)' : undefined,
  ...chartOption.value
}))

const chartOption = ref({
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
  series: [
    {
      name: '',
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
  run(v.range[0], v.range[1], option.bannedOnly, props.downloader)
})
const props = defineProps<{
  downloader?: string
}>()

const { loading, run, refresh } = useRequest(getGeoIPData, {
  defaultParams: [
    dayjs().startOf('day').add(-7, 'day').toDate(),
    new Date(),
    option.bannedOnly,
    props.downloader
  ],
  onSuccess: (data) => {
    if (data.data) {
      const fieldData = data.data[option.field]
      let processedData
      if (option.enableThreshold) {
        // 计算总和
        const totalValue = fieldData.reduce((acc, item) => acc + item.value, 0)
        // 设置1%阈值
        const threshold = totalValue * 0.01
        // 过滤掉小于阈值的项目
        processedData = fieldData.filter((item) => item.value >= threshold)
      } else {
        // 不进行过滤
        processedData = fieldData
      }
      chartOption.value.legend.data = processedData.map((it) => it.key)
      chartOption.value.series[0].data = processedData.map((it) => ({
        name:
          it.key === 'N/A' && option.field === 'province'
            ? t('page.charts.data.province.na') // 因为省份数据是中国大陆地区才有的，N/A我们就认为它们是海外或者没有收录的数据吧
            : it.key,
        value: it.value
      }))
      chartOption.value.series[0].name = t('page.charts.options.field.' + option.field)
    }
  },
  onError: (e) => {
    err.value = e
  }
})
</script>
