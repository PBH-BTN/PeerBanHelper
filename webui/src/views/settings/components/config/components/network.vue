<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.network')
    }}</a-typography-title>
    <a-space direction="vertical" fill>
      <a-typography-title :heading="4">DNS</a-typography-title>
      <a-form-item
        :label="t('page.settings.tab.config.reslolver.useSystem')"
        field="resolver.use_system"
        disabled
      >
        <a-switch v-model="resolverModel.use_system" />
      </a-form-item>
      <formArray
        v-if="resolverModel.use_system === false"
        v-model="resolverModel.servers"
        :label="t('page.settings.tab.config.reslolver.customServer')"
        :pagination-props="{ pageSize: 10, total: resolverModel?.servers?.length ?? 0 }"
      />
    </a-space>
    <a-space direction="vertical" fill>
      <a-typography-title :heading="4">{{
        t('page.settings.tab.config.proxy')
      }}</a-typography-title>
      <a-form-item :label="t('page.settings.tab.config.proxy.type')" field="proxy.setting">
        <a-radio-group v-model="proxyModel.setting" type="button">
          <a-radio v-for="type in supportedProxyType" :key="type" :value="type">{{
            t(`page.settings.tab.config.proxy.type.${type}`)
          }}</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item
        :label="t('page.settings.tab.config.proxy.host')"
        field="proxy.host"
        validate-trigger="focus"
        :disabled="proxyModel.setting < ProxySetting.HTTP_PROXY"
      >
        <a-input v-model="proxyModel.host" style="width: 150px" />
      </a-form-item>
      <a-form-item
        :label="t('page.settings.tab.config.proxy.port')"
        field="proxy.port"
        validate-trigger="blur"
        :disabled="proxyModel.setting < ProxySetting.HTTP_PROXY"
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
          v-model="proxyModel.port"
          :max="65535"
          :min="1"
          style="width: 100px"
        ></a-input-number>
      </a-form-item>
      <a-form-item
        :label="t('page.settings.tab.config.proxy.non_proxy_hosts')"
        field="proxy.non_proxy_hosts"
        :disabled="proxyModel.setting === ProxySetting.NO_PROXY"
      >
        <a-textarea
          v-model="proxyModel.non_proxy_hosts"
          :placeholder="
            t('page.settings.tab.config.proxy.non_proxy_hosts.tips', { separator: '|' })
          "
        />
      </a-form-item>
    </a-space>
  </a-space>
</template>
<script setup lang="ts">
import { ProxySetting, type Proxy, type Resolvers } from '@/api/model/config'
import { useI18n } from 'vue-i18n'
import formArray from '../../profile/components/formArray.vue'

const supportedProxyType = [
  ProxySetting.NO_PROXY,
  ProxySetting.SYSTEM_PROXY,
  ProxySetting.HTTP_PROXY,
  ProxySetting.SOCKS_PROXY
]
const { t } = useI18n()
const proxyModel = defineModel<Proxy>('proxy', { required: true })
const resolverModel = defineModel<Resolvers>('resolvers', { required: true })
</script>
