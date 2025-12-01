<template>
  <a-space direction="vertical" fill>
    <a-form-item
      :label="t('page.settings.tab.profile.module.enable')"
      :tooltip="t('page.settings.tab.profile.module.peerAnalyseService.swarmTracking.tips')"
      field="model.enabled"
    >
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
        v-if="model.enabled"
        :label="
        t('page.settings.tab.profile.module.peerAnalyseService.swarmTracking.dataFlushInterval')
      "
        :tooltip="
        t('page.settings.tab.profile.module.peerAnalyseService.swarmTracking.dataFlushInterval')
      "
        field="model.data_flush_interval"
    >
      <a-input-number v-model="model.data_flush_interval" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }}</template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_flush_interval) }}</template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type {SwarmTracking} from '@/api/model/profile'
import {useI18n} from 'vue-i18n'
import {formatMilliseconds} from '@/utils/time.ts'

const { t } = useI18n()
const model = defineModel<SwarmTracking>({ required: true })
</script>
