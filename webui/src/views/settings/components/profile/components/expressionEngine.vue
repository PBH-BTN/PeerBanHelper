<template>
  <a-space direction="vertical" fill>
    <a-form-item
      :label="t('page.settings.tab.profile.module.enable')"
      field="model.enabled"
      :tooltip="t('page.settings.tab.profile.module.expressionEngine.tips')"
    >
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.expressionEngine.useGlobalBanTime')"
      field="model.ban_duration"
    >
      <a-space>
        <a-switch v-model="useGlobalBanTime" />
        <a-input-number v-if="!useGlobalBanTime" v-model="model.ban_duration as number">
          <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
        </a-input-number>
      </a-space>
      <template v-if="model.ban_duration !== 'default'" #extra>
        ={{ formatMilliseconds(model.ban_duration) }}
      </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ExpressionEngine } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const model = defineModel<ExpressionEngine>({ required: true })

const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})
</script>
