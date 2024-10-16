<template>
    <a-space direction="vertical" fill>
      <a-form-item
        :label="t('page.settings.tab.config.module.enable')"
        field="model.enabled"
      >
        <a-switch v-model="model.enabled" />
      </a-form-item>
      <a-form-item
        :label="t('page.settings.tab.config.module.ruleSubscribe.individualBanTime')"
        field="model.ban_duration"
      >
        <a-space>
          <a-switch v-model="individualBanTime" @change="changeIndividualBanTime" />
          <a-input-number v-if="!individualBanTime" v-model="model.ban_duration as number">
            <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
          </a-input-number>
        </a-space>
        <template v-if="model.ban_duration !== 'default'" #extra>
          ={{ formatMilliseconds(model.ban_duration) }}
        </template>
      </a-form-item>
          <a-typography-text>
      <i18n-t keypath="page.settings.tab.config.module.ruleSubscribe.subscribe">
        <template #link>
          <a-link @click="goto('rule_management_subscribe')">{{
            t('page.settings.tab.config.module.ruleSubscribe.subscribe.link')
          }}</a-link>
        </template>
      </i18n-t>
    </a-typography-text>
    </a-space>
  </template>
  <script setup lang="ts">
  import type { IpAddressBlockerRules } from '@/api/model/settings';
import { useViewRoute } from '@/router';
import { formatMilliseconds } from '@/utils/time';
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
const [_, _currentName, goto] = useViewRoute()
  
  const { t } = useI18n()
  
  const model = defineModel<IpAddressBlockerRules>({ required: true })
  const individualBanTime = ref(model.value.ban_duration === 'default')
  const changeIndividualBanTime = (value: string | number | boolean) => {
    if (value) {
      model.value.ban_duration = 'default'
    } else {
      model.value.ban_duration = 259200000
    }
  }
  </script>
  