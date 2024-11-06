<template>
  <a-tabs :default-active-key="0" lazy-load animation type="rounded">
    <a-tab-pane :key="0" :title="t('page.charts.all')">
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
          <banTrends />
        </a-col>
        <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
          <fieldPie />
        </a-col>
        <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
          <plusWarpper :title="t('page.charts.title.geoip')">
            <ispPie />
          </plusWarpper>
        </a-col>
        <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
          <plusWarpper :title="t('page.charts.title.traffic')">
            <traffic />
          </plusWarpper>
        </a-col>
        <a-col :xl="24" :lg="24" :md="24" :sm="24" :xs="24">
          <plusWarpper :title="t('page.charts.title.trends')"> <trends /></plusWarpper>
        </a-col>
      </a-row>
    </a-tab-pane>
    <a-tab-pane
      v-for="(downloader, i) of downloaderList?.data ?? []"
      :key="i + 1"
      :title="downloader.name"
    >
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
          <banTrends :downloader="downloader.name" />
        </a-col>
        <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
          <fieldPie :downloader="downloader.name" />
        </a-col>
        <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
          <plusWarpper :title="t('page.charts.title.geoip')">
            <ispPie :downloader="downloader.name" />
          </plusWarpper>
        </a-col>
        <a-col :xl="12" :lg="24" :md="24" :sm="24" :xs="24">
          <plusWarpper :title="t('page.charts.title.traffic')">
            <traffic :downloader="downloader.name" />
          </plusWarpper>
        </a-col>
        <a-col :xl="24" :lg="24" :md="24" :sm="24" :xs="24">
          <plusWarpper :title="t('page.charts.title.trends')">
            <trends :downloader="downloader.name"
          /></plusWarpper>
        </a-col>
      </a-row>
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import { getDownloaders } from '@/service/downloaders'
import ispPie from '@/views/charts/components/ispPie.vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'
import banTrends from './components/banTrends.vue'
import fieldPie from './components/fieldPie.vue'
import plusWarpper from './components/plusWarpper.vue'
import traffic from './components/traffic.vue'
import trends from './components/trends.vue'

const { t } = useI18n()

const { data: downloaderList } = useRequest(getDownloaders)
</script>

<style>
.chart {
  height: 440px;
}
.chart-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
</style>
