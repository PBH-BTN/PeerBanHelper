<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.lookup.title')
    }}</a-typography-title>
    <a-form-item
      :label="t('page.settings.tab.config.lookup.dnsReverseLookup')"
      field="server.dns_reverse_lookup"
      :tooltip="t('page.settings.tab.config.lookup.dnsReverseLookup')"
    >
      <a-switch v-model="model.dns_reverse_lookup" />
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type Lookup } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<Lookup>({ required: true })
</script>
