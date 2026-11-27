<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.banlistPrefill.title')
    }}</a-typography-title>
    <a-typography-text>
      {{ t('page.settings.tab.config.banlistPrefill.description') }}
    </a-typography-text>
    <a-form-item
      :label="t('page.settings.tab.config.banlistPrefill.enabled')"
      field="banlist_prefill.enabled"
    >
      <a-switch v-model="model.enabled" />
      <template #extra>
        {{ t('page.settings.tab.config.banlistPrefill.enabled.tips') }}
      </template>
    </a-form-item>
  </a-space>
</template>

<script setup lang="ts">
import { type BanlistPrefill } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<BanlistPrefill>({ required: true })
</script>
