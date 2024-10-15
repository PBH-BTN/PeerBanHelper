<template>
  <a-space direction="vertical" fill>
    <a-typography-title id="module" :heading="3">{{
      t('page.settings.tab.config.module.progressCheatBlocker')
    }}</a-typography-title>
    <a-alert>{{ t('page.settings.tab.config.module.progressCheatBlocker.tips') }}</a-alert>
    <a-form-item :label="t('page.settings.tab.config.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.minSize')"
      field="model.minimum_size"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.minSize.tips')"
    >
      <a-input-number v-model="model.minimum_size" style="width: 200px">
        <template #suffix> Bytes </template>
      </a-input-number>
      <template #extra> ≈ {{ formatFileSize(model.minimum_size) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.maxDifference')"
      field="model.maximum_difference"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.maxDifference.tips')"
    >
      <a-space>
        <a-slider
          v-model="model.maximum_difference"
          style="width: 250px"
          :step="1"
          :min="0"
          :max="100"
          :format-tooltip="(value: number) => `${value}%`"
        ></a-slider>
        <br />{{ model.maximum_difference }}%
      </a-space>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.progressRewindDetection')"
    >
      <a-switch
        :default-checked="model.rewind_maximum_difference !== -1"
        @change="
          (value) => {
            if (value) model.rewind_maximum_difference = 7
            else model.rewind_maximum_difference = -1
          }
        "
      />
    </a-form-item>
    <a-form-item
      v-if="model.rewind_maximum_difference !== -1"
      :label="t('page.settings.tab.config.module.progressCheatBlocker.rewindMaxDifference')"
      field="model.rewind_maximum_difference"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.rewindMaxDifference.tips')"
    >
      <a-space>
        <a-slider
          v-model="model.rewind_maximum_difference"
          style="width: 250px"
          :step="1"
          :min="0"
          :max="100"
          :format-tooltip="(value: number) => `${value}%`"
        ></a-slider>
        <br />{{ model.rewind_maximum_difference }}%
      </a-space>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients')"
      field="model.block_excessive_clients"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients.tips')">
        <a-switch v-model="model.block_excessive_clients"></a-switch>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.excessive_threshold')"
      field="model.excessive_threshold"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.excessive_threshold.tips')">
        <a-input-number v-model="model.excessive_threshold" style="width: 100px;"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.ipv4prefixlength')"
      field="model.ipv4_prefix_length"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.ipprefixLength.tips')">
        <a-input-number v-model="model.ipv4_prefix_length" style="width: 100px;"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.ipv4prefixlength')"
      field="model.ipv6_prefix_length"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.ipprefixLength.tips')">
        <a-input-number v-model="model.ipv6_prefix_length" style="width: 100px;"></a-input-number>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ProgressCheatBlocker } from '@/api/model/settings';
import { formatFileSize } from '@/utils/file';
import { useI18n } from 'vue-i18n';

const { t } = useI18n()
const model = defineModel<ProgressCheatBlocker>({ required: true })
</script>
