<template>
  <a-space direction="vertical" fill>
    <a-form-item
      :label="t('page.settings.tab.config.module.enable')"
      field="model.enabled"
      :tooltip="t('page.settings.tab.config.module.autoRangeBan.tips')"
    >
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.autoRangeBan.individualBanTime')"
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
    <a-form-item
      :label="t('page.settings.tab.config.module.autoRangeBan.ipv4Prefix')"
      field="model.ipv4"
    >
      <a-input-number v-model="model.ipv4" style="width: 100px"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.autoRangeBan.ipv6Prefix')"
      field="model.ipv6"
    >
      <a-input-number v-model="model.ipv6" style="width: 100px"></a-input-number>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type AutoRangeBan } from '@/api/model/settings';
import { formatMilliseconds } from '@/utils/time';
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n()
const model = defineModel<AutoRangeBan>({ required: true })
const individualBanTime = ref(model.value.ban_duration === 'default')
const changeIndividualBanTime = (value: string | number | boolean) => {
  if (value) {
    model.value.ban_duration = 'default'
  } else {
    model.value.ban_duration = 259200000
  }
}
</script>
