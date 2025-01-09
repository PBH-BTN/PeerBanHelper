<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.server.title')
    }}</a-typography-title>
    <a-form-item
      :label="t('page.settings.tab.config.server.address')"
      field="server.address"
      required
      validate-trigger="focus"
    >
      <a-input v-model="model.address" style="width: 100px" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.server.port')"
      field="server.http"
      required
      validate-trigger="focus"
      :rules="[
        {
          max: 65535,
          message: t('page.settings.tab.config.server.port.error')
        },
        {
          min: 1,
          message: t('page.settings.tab.config.server.port.error')
        }
      ]"
    >
      <a-input-number
        v-model="model.http"
        :max="65535"
        :min="1"
        style="width: 100px"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.server.prefix')"
      :tooltip="t('page.settings.tab.config.server.prefix.tips')"
      field="server.prefix"
      required
      validate-trigger="focus"
      :rules="[
        { type: 'url', required: true },
        { match: /[^\/]$/, message: t('page.settings.tab.config.server.prefix.error') }
      ]"
    >
      <a-input v-model="model.prefix" style="width: 200px" />
    </a-form-item>
    <a-form-item label="Token" field="server.token" required>
      <a-input-password v-model="model.token" style="width: 350px" />
    </a-form-item>
    <a-form-item :label="t('page.settings.tab.config.server.cors')" field="server.allow_cors">
      <a-switch v-model="model.allow_cors" />
      <template v-if="model.allow_cors" #extra>
        <a-typography-text type="warning">
          {{ t('page.settings.tab.config.server.cors.tips') }}
        </a-typography-text>
      </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type Server } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<Server>({ required: true })
</script>
