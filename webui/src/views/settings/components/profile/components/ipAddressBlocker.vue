<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.ipAddressBlocker.useGlobalBanTime')"
      field="model.ban_duration"
    >
      <a-space>
        <a-switch v-model="useGlobalBanTime" />
        <a-input-number v-if="!useGlobalBanTime" v-model.number="model.ban_duration as number">
          <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
        </a-input-number>
      </a-space>
      <template v-if="model.ban_duration !== 'default'" #extra>
        ={{ formatMilliseconds(model.ban_duration) }}
      </template>
    </a-form-item>
    <a-typography-text>
      <i18n-t keypath="page.settings.tab.profile.module.ipAddressBlocker.rules">
        <template #link>
          <a-link @click="goto('rule_management_ip')">{{
            t('page.settings.tab.profile.module.ipAddressBlocker.rules.link')
          }}</a-link>
        </template>
      </i18n-t>
    </a-typography-text>
  </a-space>
</template>
<script setup lang="ts">
import { type IpAddressBlocker } from '@/api/model/profile'
import { useViewRoute } from '@/router'
import { formatMilliseconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
const [_, _currentName, goto] = useViewRoute()

const { t } = useI18n()
const model = defineModel<IpAddressBlocker>({ required: true })
const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})
</script>
