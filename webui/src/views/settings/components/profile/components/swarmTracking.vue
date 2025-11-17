<template>
  <a-space direction="vertical" fill>
    <a-form-item
      :label="t('page.settings.tab.profile.module.enable')"
      :tooltip="t('page.settings.tab.profile.module.peerAnalyseService.swarmTracking.tips')"
      field="model.enabled"
    >
      <a-switch v-model="model.enabled" />
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { SwarmTracking } from '@/api/model/profile'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const model = defineModel<SwarmTracking>({ required: true })
</script>
