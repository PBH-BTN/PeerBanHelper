<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.ipDatabase.title')
    }}</a-typography-title>
    <a-form-item
      :label="t('page.settings.tab.config.ipDatabase.autoUpdate')"
      field="ip_database.auto_update"
    >
      <a-switch v-model="model.auto_update" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.ipDatabase.city')"
      field="ip_database.database_city"
    >
      <a-input v-model="model.database_city" disabled style="width: 150px" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.ipDatabase.asn')"
      field="ip_database.database_asn"
    >
      <a-input v-model="model.database_asn" disabled style="width: 150px" />
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type IpDatabase } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<IpDatabase>({ required: true })
</script>
