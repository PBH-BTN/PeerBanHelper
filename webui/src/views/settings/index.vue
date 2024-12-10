<template>
  <a-tabs :default-active-key="defaultActiveKey" lazy-load animation type="rounded">
    <a-tab-pane :key="0">
      <template #title>
        <IconFont type="icon-icon_status" /> &nbsp;{{ t('page.settings.tab.info.title') }}
      </template>
      <RunningStatus />
    </a-tab-pane>
    <a-tab-pane :key="1">
      <template #title> <icon-settings />&nbsp; {{ t('page.settings.tab.config') }} </template>
      <ConfigForm />
    </a-tab-pane>
    <a-tab-pane :key="2">
      <template #title>
        <IconFont type="icon-config" /> &nbsp;{{ t('page.settings.tab.profile') }}
      </template>
      <ProfileForm />
    </a-tab-pane>
    <a-tab-pane :key="3">
      <template #title> <icon-experiment /> &nbsp;{{ t('page.settings.tab.labs') }} </template>
      <Labs />
    </a-tab-pane>
  </a-tabs>
</template>
<script setup lang="ts">
import IconFont from '@/components/iconFont'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ConfigForm from './components/config/index.vue'
import RunningStatus from './components/info/index.vue'
import Labs from './components/labs/index.vue'
import ProfileForm from './components/profile/index.vue'
const { t } = useI18n()

const { query } = useRoute()
const defaultActiveKey = query.tab
  ? ({
      info: 0,
      config: 1,
      profile: 2,
      lab: 3
    }[query.tab as string] ?? 0)
  : 0
</script>
