<template>
  <a-space direction="vertical" fill>
    <a-typography-title id="id" :heading="3">{{
      t('page.settings.tab.config.logger.title')
    }}</a-typography-title>
    <a-form-item
      :label="t('page.settings.tab.config.logger.hide_finish_log')"
      field="server.hide_finish_log"
      :tooltip="t('page.settings.tab.config.logger.hide_finish_log.tips')"
    >
      <a-switch v-model="model.hide_finish_log" />
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type Logger } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<Logger>({ required: true })
</script>
