<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.useGlobalBanTime')"
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
    <a-form-item
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-length')"
      field="model.subnet_mask_length"
    >
      <a-input-number
        v-model="model.subnet_mask_length"
        style="width: 100px"
        :min="0"
        :max="32"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.subnet-mask-v6-length')"
      field="model.subnet_mask_v6_length"
    >
      <a-input-number
        v-model="model.subnet_mask_v6_length"
        style="width: 100px"
        :min="0"
        :max="128"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.tolerate-num')"
      :tooltip="t('page.settings.tab.profile.module.multiDialingBlocker.tolerate-num.tips')"
      field="model.tolerate_num"
    >
      <a-input-number
        v-model="model.tolerate_num"
        style="width: 100px"
        :min="0"
        :step="1"
      ></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.timeWindow')"
      field="model.cache_lifespan"
    >
      <a-input-number v-model="model.cache_lifespan" style="width: 100px" :min="0">
        <template #suffix> {{ t('page.settings.tab.profile.unit.s') }} </template>
      </a-input-number>
      <template #extra> ={{ formatSeconds(model.cache_lifespan) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.keep-hunting')"
      :tooltip="t('page.settings.tab.profile.module.multiDialingBlocker.keep-hunting.tips')"
      field="model.keep_hunting"
    >
      <a-switch v-model="model.keep_hunting" />
    </a-form-item>
    <a-form-item
      v-if="model.keep_hunting"
      :label="t('page.settings.tab.profile.module.multiDialingBlocker.keep-hunting-time')"
      field="model.keep_hunting_time"
    >
      <a-input-number v-model="model.keep_hunting_time" :min="0" style="width: 150px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.s') }} </template>
      </a-input-number>
      <template #extra> ={{ formatSeconds(model.keep_hunting_time) }} </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { MultiDialingBlocker } from '@/api/model/profile'
import { formatMilliseconds, formatSeconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const model = defineModel<MultiDialingBlocker>({ required: true })
const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 259200000
  }
})
</script>
