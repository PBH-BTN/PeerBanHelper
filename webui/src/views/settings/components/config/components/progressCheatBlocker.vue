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
      :tooltip="
        t('page.settings.tab.config.module.progressCheatBlocker.block_excessive_clients.tips')
      "
    >
      <a-switch v-model="model.block_excessive_clients"></a-switch>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.excessive_threshold')"
      field="model.excessive_threshold"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.excessive_threshold.tips')"
    >
      <a-input-number v-model="model.excessive_threshold" style="width: 100px"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.ipv4prefixlength')"
      field="model.ipv4_prefix_length"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.ipprefixLength.tips')"
    >
      <a-input-number v-model="model.ipv4_prefix_length" style="width: 100px"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.ipv4prefixlength')"
      field="model.ipv6_prefix_length"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.ipprefixLength.tips')"
    >
      <a-input-number v-model="model.ipv6_prefix_length" style="width: 100px"></a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.banDuration')"
      field="model.ban_duration"
    >
      <a-input-number v-model="model.ban_duration" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.ban_duration) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.enablePersist')"
      field="model.enable_persist"
    >
      <a-switch v-model="model.enable_persist"></a-switch>
      <template v-if="model.enable_persist" #extra>
        <a-typography-text type="danger">{{
          t('page.settings.tab.config.module.progressCheatBlocker.enablePersist.tips')
        }}</a-typography-text></template
      >
    </a-form-item>
    <a-form-item
      v-if="model.enable_persist"
      :label="t('page.settings.tab.config.module.progressCheatBlocker.persistDuration')"
      field="model.persist_duration"
    >
      <a-input-number v-model="model.persist_duration" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.persist_duration) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.maxWaitDuration')"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.maxWaitDuration.tips')"
      field="model.max_wait_duration"
    >
      <a-input-number v-model="model.max_wait_duration" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.max_wait_duration) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.config.module.progressCheatBlocker.enableFastPCBTest')"
      :tooltip="t('page.settings.tab.config.module.progressCheatBlocker.enableFastPCBTest.tips')"
    >
      <a-switch
        :default-checked="model.fast_pcb_test_percentage !== -1"
        @change="
          (value) => {
            if (value) model.fast_pcb_test_percentage = 10
            else model.fast_pcb_test_percentage = -1
          }
        "
      />
    </a-form-item>
    <a-form-item
      v-if="model.fast_pcb_test_percentage !== -1"
      :label="t('page.settings.tab.config.module.progressCheatBlocker.fastPCBTestPercentage')"
      field="model.fast_pcb_test_percentage"
    >
      <a-space>
        <a-slider
          v-model="model.fast_pcb_test_percentage"
          style="width: 250px"
          :step="1"
          :min="0"
          :max="100"
          :format-tooltip="(value: number) => `${value}%`"
        ></a-slider>
        <br />{{ model.fast_pcb_test_percentage }}%
      </a-space>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ProgressCheatBlocker } from '@/api/model/settings';
import { formatFileSize } from '@/utils/file';
import { formatMilliseconds } from '@/utils/time';
import { useI18n } from 'vue-i18n';

const { t } = useI18n()
const model = defineModel<ProgressCheatBlocker>({ required: true })
</script>
