<template>
  <a-row
    justify="center"
    align="stretch"
    :wrap="true"
    :gutter="[
      { xs: 12, sm: 12, md: 12, lg: 12, xl: 24 },
      { xs: 12, sm: 12, md: 12, lg: 12, xl: 24 }
    ]"
  >
    <a-col v-if="showCharts.banTrends" :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <banTrends :downloader="downloader" />
    </a-col>
    <a-col v-if="showCharts.fieldPie" :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <fieldPie :downloader="downloader" />
    </a-col>
    <a-col v-if="showCharts.ispPie" :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.geoip')">
        <ispPie :downloader="downloader" />
      </plusWarpper>
    </a-col>
    <a-col v-if="showCharts.traffic" :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.traffic')">
        <traffic :downloader="downloader" />
      </plusWarpper>
    </a-col>
    <a-col v-if="showCharts.trends" :xl="24" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.trends')">
        <trends :downloader="downloader" />
      </plusWarpper>
    </a-col>
    <a-col v-if="showCharts.sessionDayBucket" :xl="24" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.sessionDayBucket')">
        <sessionDayBucket :downloader="downloader" />
      </plusWarpper>
    </a-col>
  </a-row>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import banTrends from './components/banTrends.vue'
import fieldPie from './components/fieldPie.vue'
import ispPie from './components/ispPie.vue'
import plusWarpper from './components/plusWarpper.vue'
import sessionDayBucket from './components/sessionDayBucket.vue'
import traffic from './components/traffic.vue'
import trends from './components/trends.vue'
const { t } = useI18n()
const props = defineProps<{
  downloader: string
  showCharts: {
    banTrends: boolean
    fieldPie: boolean
    ispPie: boolean
    traffic: boolean
    trends: boolean
    sessionDayBucket: boolean
  }
}>()
const downloader = computed((): string | undefined =>
  props.downloader === 'all' ? undefined : props.downloader
)
</script>
