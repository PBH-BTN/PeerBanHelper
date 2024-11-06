<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.peerIdBlackList.useGlobalBanTime')"
      field="model.ban_duration"
    >
      <a-space>
        <a-switch v-model="useGlobalBanTime" />
        <a-input-number
          v-if="!useGlobalBanTime"
          v-model.number="model.ban_duration as number"
          :min="1"
        >
          <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
        </a-input-number>
      </a-space>
      <template v-if="model.ban_duration !== 'default'" #extra>
        ={{ formatMilliseconds(model.ban_duration) }}
      </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.btn.allowScript')"
      field="model.enabled"
    >
      <a-switch v-model="model.allow_script_execute" />
      <template v-if="model.allow_script_execute" #extra>
        <a-typography-text type="danger">
          {{ t('page.settings.tab.profile.module.btn.allowScript.tips') }}
        </a-typography-text></template
      >
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type Btn } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const model = defineModel<Btn>({ required: true })
const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})
</script>
