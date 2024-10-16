<template>
  <a-space direction="vertical" fill>
    <a-typography-title id="id" :heading="3">{{
      t('page.settings.tab.config.persist.title')
    }}</a-typography-title>
    <a-form-item
      :label="t('page.settings.tab.config.persist.banlist')"
      field="persist.ban_logs_keep_days"
    >
      <a-switch v-model="model.banlist" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.persist.ban_logs_keep_days')"
      field="persist.ban_logs_keep_days"
    >
      <a-input-number v-model="model.ban_logs_keep_days" style="width: 100px">
        <template #suffix> {{ t('page.settings.tab.config.unit.day') }} </template>
      </a-input-number>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type Persist } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<Persist>({ required: true })
</script>
