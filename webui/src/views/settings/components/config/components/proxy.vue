<template>
  <a-space direction="vertical" fill>
    <a-typography-title id="id" :heading="3">{{
      t('page.settings.tab.config.proxy')
    }}</a-typography-title>
    <a-form-item :label="t('page.settings.tab.config.proxy.type')" field="proxy.setting">
      <a-radio-group v-model="model.setting" type="button">
        <a-radio v-for="type in supportedProxyType" :key="type" :value="type">{{
          t(`page.settings.tab.config.proxy.type.${type}`)
        }}</a-radio>
      </a-radio-group>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.proxy.host')"
      field="proxy.host"
      validate-trigger="focus"
      :rules="[{ type: 'ip' }]"
      :disabled="model.setting < ProxySetting.HTTP_PROXY"
    >
      <a-input v-model="model.host" style="width: 150px" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.proxy.port')"
      field="proxy.port"
      validate-trigger="focus"
      :disabled="model.setting < ProxySetting.HTTP_PROXY"
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
        v-model="model.port"
        :max="65535"
        :min="1"
        style="width: 100px"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.proxy.non_proxy_hosts')"
      field="proxy.non_proxy_hosts"
      :disabled="model.setting === ProxySetting.NO_PROXY"
    >
      <a-textarea
        v-model="model.non_proxy_hosts"
        :placeholder="t('page.settings.tab.config.proxy.non_proxy_hosts.tips', { separator: '|' })"
      />
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { ProxySetting, type Proxy } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const supportedProxyType = [
  ProxySetting.NO_PROXY,
  ProxySetting.SYSTEM_PROXY,
  ProxySetting.HTTP_PROXY,
  ProxySetting.SOCKS_PROXY
]
const { t } = useI18n()
const model = defineModel<Proxy>({ required: true })
</script>
