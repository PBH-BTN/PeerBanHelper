<template>
  <a-tabs :default-active-key="defaultActiveKey" lazy-load animation type="rounded">
    <a-tab-pane :key="0" :title="t('page.settings.tab.info.title')">
      <RunningStatus />
    </a-tab-pane>
    <a-tab-pane :key="1" :title="t('page.settings.tab.config')">
      <ConfigForm />
    </a-tab-pane>
    <a-tab-pane :key="2" :title="t('page.settings.tab.profile')">
      <ProfileForm />
    </a-tab-pane>
  </a-tabs>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ConfigForm from './components/config/index.vue'
import RunningStatus from './components/info/index.vue'
import ProfileForm from './components/profile/index.vue'
const { t } = useI18n()

const { query } = useRoute()
const defaultActiveKey = query.tab
  ? ({
      info: 0,
      config: 1,
      profile: 2
    }[query.tab as string] ?? 0)
  : 0
</script>
