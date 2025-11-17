<template>
  <a-space direction="vertical" fill>
    <a-form-item
      :label="t('page.settings.tab.profile.module.enable')"
      :tooltip="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.enable.tips')"
      field="model.enabled"
    >
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataFlushInterval')"
      :tooltip="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataFlushInterval.tips')"
      field="model.data_flush_interval"
    >
      <a-input-number v-model="model.data_flush_interval" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_flush_interval) }} </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataRetentionTime')"
      :tooltip="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataRetentionTime.tips')"
      field="model.data_retention_time"
    >
      <a-input-number v-model="model.data_retention_time" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_retention_time) }} </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataCleanupInterval')"
      :tooltip="t('page.settings.tab.profile.module.peerAnalyseService.peerRecording.dataCleanupInterval.tips')"
      field="model.data_cleanup_interval"
    >
      <a-input-number v-model="model.data_cleanup_interval" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_cleanup_interval) }} </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { PeerRecording } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const model = defineModel<PeerRecording>({ required: true })
</script>
