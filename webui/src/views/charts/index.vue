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
    <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <banLine />
    </a-col>
    <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <fieldPie />
    </a-col>
    <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.trends')"> <trends /></plusWarpper>
    </a-col>
    <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.traffic')">
        <traffic />
      </plusWarpper>
    </a-col>
    <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <plusWarpper :title="t('page.charts.title.geoip')">
        <ispPie />
      </plusWarpper>
    </a-col>
    <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
      <game2048 />
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import banLine from './components/banLine.vue'
import fieldPie from './components/fieldPie.vue'
import trends from './components/trends.vue'
import traffic from './components/traffic.vue'
import plusWarpper from './components/plusWarpper.vue'
import game2048 from './components/2048.vue'
import { useI18n } from 'vue-i18n'
import ispPie from '@/views/charts/components/ispPie.vue'

const { t } = useI18n()
</script>

<style>
.chart {
  height: 440px;
}
</style>
