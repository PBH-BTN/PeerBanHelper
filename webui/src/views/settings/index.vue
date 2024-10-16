<template>
  <a-tabs default-active-key="1" lazy-load>
    <a-tab-pane key="1" :title="t('page.settings.tab.config')"> <ConfigForm /></a-tab-pane>
    <a-tab-pane key="2" :title="t('page.settings.tab.profile')">
      Content of Tab Panel 1
    </a-tab-pane>
    <a-tab-pane key="3">
      <template #title
        ><icon-code-square />
        {{ t('page.settings.tab.script') }}
      </template>
      Content of Tab Panel 1
    </a-tab-pane>
  </a-tabs>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ConfigForm from './components/config/index.vue'

const { t } = useI18n()
</script>
